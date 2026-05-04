package com.insurance.aml.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.insurance.aml.module.system.mapper.SysDictItemMapper;
import com.insurance.aml.module.system.mapper.SysDictMapper;
import com.insurance.aml.module.system.model.entity.SysDict;
import com.insurance.aml.module.system.model.entity.SysDictItem;
import com.insurance.aml.module.system.service.DictService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;


/**
 * 数据字典服务实现
 * 使用Redis缓存字典数据，提升查询性能
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DictServiceImpl implements DictService {
    private final SysDictMapper sysDictMapper;
    private final SysDictItemMapper sysDictItemMapper;

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
    @Cacheable(value = "dict", key = "'dict::' + #dictCode")
    public List<SysDictItem> getDictItems(String dictCode) {
        log.debug("查询字典项，dictCode={}", dictCode);
        List<SysDictItem> items = queryDictItemsFromDb(dictCode);
        return items;
    }

    /**
     * 刷新所有字典缓存到Redis
     */
    @Override
    @CacheEvict(value = "dict", allEntries = true)
    public void refreshDictCache() {
        log.info("字典缓存已全部清除，下次访问将自动从数据库加载");
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
