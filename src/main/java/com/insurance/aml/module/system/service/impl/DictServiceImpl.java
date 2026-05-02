package com.insurance.aml.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.module.system.mapper.SysDictItemMapper;
import com.insurance.aml.module.system.mapper.SysDictMapper;
import com.insurance.aml.module.system.model.entity.SysDict;
import com.insurance.aml.module.system.model.entity.SysDictItem;
import com.insurance.aml.module.system.service.DictService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 数据字典服务实现
 * 使用Redis缓存字典数据，提升查询性能
 */
@Slf4j
@Service
public class DictServiceImpl implements DictService {

    private static final String DICT_CACHE_KEY_PREFIX = "aml:dict:";
    private static final long CACHE_EXPIRE_HOURS = 24;

    @Autowired
    private SysDictMapper sysDictMapper;

    @Autowired
    private SysDictItemMapper sysDictItemMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 查询所有字典列表
     */
    @Override
    public List<SysDict> listDicts() {
        LambdaQueryWrapper<SysDict> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDict::getStatus, "ACTIVE");
        wrapper.orderByAsc(SysDict::getDictCode);
        return sysDictMapper.selectList(wrapper);
    }

    /**
     * 根据字典编码查询字典项
     * 优先从Redis缓存获取，缓存未命中时查询数据库并回填缓存
     */
    @Override
    public List<SysDictItem> getDictItems(String dictCode) {
        String cacheKey = DICT_CACHE_KEY_PREFIX + dictCode;

        // 尝试从Redis缓存获取
        try {
            String cachedData = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.debug("从Redis缓存获取字典项，dictCode={}", dictCode);
                return objectMapper.readValue(cachedData, new TypeReference<List<SysDictItem>>() {});
            }
        } catch (JsonProcessingException e) {
            log.warn("反序列化字典缓存失败，dictCode={}，错误：{}", dictCode, e.getMessage());
        } catch (Exception e) {
            log.warn("Redis缓存读取失败，降级查询数据库，dictCode={}，错误：{}", dictCode, e.getMessage());
        }

        // 缓存未命中，查询数据库
        log.debug("Redis缓存未命中，查询数据库，dictCode={}", dictCode);
        List<SysDictItem> items = queryDictItemsFromDb(dictCode);

        // 回填缓存
        if (!items.isEmpty()) {
            try {
                String json = objectMapper.writeValueAsString(items);
                stringRedisTemplate.opsForValue().set(cacheKey, json, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            } catch (JsonProcessingException e) {
                log.warn("序列化字典项缓存失败，dictCode={}，错误：{}", dictCode, e.getMessage());
            } catch (Exception e) {
                log.warn("Redis缓存写入失败，dictCode={}，错误：{}", dictCode, e.getMessage());
            }
        }

        return items;
    }

    /**
     * 刷新所有字典缓存到Redis
     */
    @Override
    public void refreshDictCache() {
        log.info("开始刷新字典缓存");

        List<SysDict> dicts = listDicts();
        int successCount = 0;

        for (SysDict dict : dicts) {
            try {
                List<SysDictItem> items = queryDictItemsFromDb(dict.getDictCode());
                String json = objectMapper.writeValueAsString(items);
                String cacheKey = DICT_CACHE_KEY_PREFIX + dict.getDictCode();
                stringRedisTemplate.opsForValue().set(cacheKey, json, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                successCount++;
            } catch (Exception e) {
                log.error("刷新字典缓存失败，dictCode={}，错误：{}", dict.getDictCode(), e.getMessage());
            }
        }

        log.info("字典缓存刷新完成，成功：{}/{}", successCount, dicts.size());
    }

    /**
     * 从数据库查询字典项
     */
    private List<SysDictItem> queryDictItemsFromDb(String dictCode) {
        // 先查字典ID
        LambdaQueryWrapper<SysDict> dictWrapper = new LambdaQueryWrapper<>();
        dictWrapper.eq(SysDict::getDictCode, dictCode)
                .eq(SysDict::getStatus, "ACTIVE");
        SysDict dict = sysDictMapper.selectOne(dictWrapper);

        if (dict == null) {
            return Collections.emptyList();
        }

        // 查字典项
        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictItem::getDictId, dict.getId())
                .eq(SysDictItem::getStatus, "ACTIVE")
                .orderByAsc(SysDictItem::getSortOrder);
        return sysDictItemMapper.selectList(wrapper);
    }
}
