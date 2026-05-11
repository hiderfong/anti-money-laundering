package com.insurance.aml.module.screening.service;

import com.insurance.aml.module.screening.mapper.WatchlistAliasMapper;
import com.insurance.aml.module.screening.mapper.WatchlistIdentityMapper;
import com.insurance.aml.common.enums.StatusEnum;
import com.insurance.aml.module.screening.mapper.WatchlistMapper;
import com.insurance.aml.module.screening.model.entity.Watchlist;
import com.insurance.aml.module.screening.model.entity.WatchlistAlias;
import com.insurance.aml.module.screening.model.entity.WatchlistIdentity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 制裁名单缓存服务
 * 将制裁名单数据缓存到Redis，避免每次筛查都查询数据库
 * 消除N+1查询问题：一次性加载所有启用的名单、别名、证件信息
 *
 * 缓存策略：
 * - key: aml:watchlist:all, TTL: 5分钟
 * - 名单数据变更时自动清除缓存（由WatchlistImportService调用）
 * - 缓存未命中时从DB批量加载并回填
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WatchlistCacheService {

    private final WatchlistMapper watchlistMapper;
    private final WatchlistAliasMapper watchlistAliasMapper;
    private final WatchlistIdentityMapper watchlistIdentityMapper;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;
    private final ObjectMapper objectMapper;

    /** Redis缓存Key */
    private static final String CACHE_KEY = "aml:watchlist:all";

    /** 缓存过期时间：5分钟 */
    private static final long CACHE_TTL_MINUTES = 5;

    /**
     * 获取所有启用的制裁名单条目（带缓存）
     * 首次调用从DB加载，后续从Redis读取
     *
     * @return 名单列表
     */
    public List<Watchlist> getAllActiveWatchlists() {
        List<Watchlist> cached = getFromCache("watchlists", new TypeReference<>() {});
        if (cached != null) {
            log.debug("从Redis缓存加载制裁名单，数量={}", cached.size());
            return cached;
        }
        return loadAndCacheWatchlists();
    }

    /**
     * 获取所有别名，按watchlistId分组（带缓存）
     *
     * @return key=watchlistId, value=该名单下的所有别名
     */
    public Map<Long, List<WatchlistAlias>> getAllAliasesGrouped() {
        Map<Long, List<WatchlistAlias>> cached = getFromCache("aliases_grouped", new TypeReference<>() {});
        if (cached != null) {
            log.debug("从Redis缓存加载别名数据");
            return cached;
        }
        return loadAndCacheAliases();
    }

    /**
     * 获取所有证件信息，按watchlistId分组（带缓存）
     *
     * @return key=watchlistId, value=该名单下的所有证件信息
     */
    public Map<Long, List<WatchlistIdentity>> getAllIdentitiesGrouped() {
        Map<Long, List<WatchlistIdentity>> cached = getFromCache("identities_grouped", new TypeReference<>() {});
        if (cached != null) {
            log.debug("从Redis缓存加载证件数据");
            return cached;
        }
        return loadAndCacheIdentities();
    }

    /**
     * 清除所有名单缓存
     * 在名单数据变更时调用（导入、更新、删除）
     */
    public void evictCache() {
        String key = CACHE_KEY;
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (stringRedisTemplate == null) {
            log.debug("Redis未启用，跳过制裁名单缓存清理");
            return;
        }
        stringRedisTemplate.delete(key);
        log.info("已清除制裁名单Redis缓存，key={}", key);
    }

    // ==================== 私有方法 ====================

    /**
     * 从Redis缓存获取数据
     * 缓存结构：所有数据存在同一个Hash key中，field区分类型
     */
    private <T> T getFromCache(String field, TypeReference<T> typeRef) {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (stringRedisTemplate == null) {
            return null;
        }
        try {
            Object raw = stringRedisTemplate.opsForHash().get(CACHE_KEY, field);
            if (raw == null) {
                return null;
            }
            return objectMapper.readValue(raw.toString(), typeRef);
        } catch (Exception e) {
            log.warn("读取名单缓存失败，将从DB加载: field={}, error={}", field, e.getMessage());
            return null;
        }
    }

    /**
     * 将数据写入Redis缓存
     */
    private void putToCache(String field, Object data) {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(data);
            stringRedisTemplate.opsForHash().put(CACHE_KEY, field, json);
            // 设置整体key的过期时间（每次写入都刷新TTL）
            stringRedisTemplate.expire(CACHE_KEY, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入名单缓存失败: field={}, error={}", field, e.getMessage());
        }
    }

    /**
     * 从DB加载所有启用的制裁名单并缓存
     * 一次性查询，无N+1
     */
    private List<Watchlist> loadAndCacheWatchlists() {
        log.info("从DB批量加载启用的制裁名单...");
        List<Watchlist> list = watchlistMapper.selectList(
                new LambdaQueryWrapper<Watchlist>()
                        .eq(Watchlist::getStatus, StatusEnum.ACTIVE.getCode())
        );
        log.info("DB加载制裁名单完成，数量={}", list.size());
        putToCache("watchlists", list);
        return list;
    }

    /**
     * 从DB加载所有别名并按watchlistId分组缓存
     * 一次性查询所有别名，内存分组，消除N+1
     */
    private Map<Long, List<WatchlistAlias>> loadAndCacheAliases() {
        log.info("从DB批量加载所有别名...");
        List<WatchlistAlias> allAliases = watchlistAliasMapper.selectList(null);
        Map<Long, List<WatchlistAlias>> grouped = allAliases.stream()
                .filter(a -> a.getWatchlistId() != null)
                .collect(Collectors.groupingBy(WatchlistAlias::getWatchlistId));
        log.info("DB加载别名完成，总数={}, 分组数={}", allAliases.size(), grouped.size());
        putToCache("aliases_grouped", grouped);
        return grouped;
    }

    /**
     * 从DB加载所有证件信息并按watchlistId分组缓存
     * 一次性查询所有证件，内存分组，消除N+1
     */
    private Map<Long, List<WatchlistIdentity>> loadAndCacheIdentities() {
        log.info("从DB批量加载所有证件信息...");
        List<WatchlistIdentity> allIdentities = watchlistIdentityMapper.selectList(null);
        Map<Long, List<WatchlistIdentity>> grouped = allIdentities.stream()
                .filter(i -> i.getWatchlistId() != null)
                .collect(Collectors.groupingBy(WatchlistIdentity::getWatchlistId));
        log.info("DB加载证件信息完成，总数={}, 分组数={}", allIdentities.size(), grouped.size());
        putToCache("identities_grouped", grouped);
        return grouped;
    }
}
