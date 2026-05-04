package com.insurance.aml.module.screening.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.insurance.aml.module.screening.mapper.WhitelistMapper;
import com.insurance.aml.module.screening.model.entity.Whitelist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 白名单缓存服务
 * 使用Spring Cache + @Cacheable注解实现白名单数据缓存
 *
 * 缓存策略：
 * - 缓存空间: whitelist
 * - key: whitelist::all, TTL: 5分钟
 * - 白名单变更时清除缓存
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhitelistCacheService {

    private final WhitelistMapper whitelistMapper;

    /**
     * 获取所有活跃白名单（带缓存，TTL 5分钟）
     * 缓存key: aml:whitelist:whitelist::all
     *
     * @return 活跃白名单列表
     */
    @Cacheable(value = "whitelist", key = "'whitelist::all'")
    public List<Whitelist> getAllActiveWhitelists() {
        log.debug("从数据库加载全部活跃白名单");
        List<Whitelist> list = whitelistMapper.selectList(
                new LambdaQueryWrapper<Whitelist>()
                        .eq(Whitelist::getReviewStatus, "ACTIVE")
        );
        log.debug("加载活跃白名单完成，数量={}", list.size());
        return list;
    }

    /**
     * 清除白名单缓存
     * 在白名单数据变更时调用（新增、修改、删除白名单）
     */
    @CacheEvict(value = "whitelist", key = "'whitelist::all'")
    public void evictWhitelistCache() {
        log.info("白名单缓存已清除");
    }
}
