package com.insurance.aml.module.system.service;

import com.insurance.aml.module.system.model.entity.SysDict;
import com.insurance.aml.module.system.model.entity.SysDictItem;

import java.util.List;

/**
 * 数据字典服务接口
 */
public interface DictService {

    /**
     * 查询所有字典列表
     *
     * @return 字典列表
     */
    List<SysDict> listDicts();

    /**
     * 根据字典编码查询字典项列表
     * 优先从Redis缓存获取，缓存未命中时查询数据库
     *
     * @param dictCode 字典编码
     * @return 字典项列表
     */
    List<SysDictItem> getDictItems(String dictCode);

    /**
     * 刷新Redis中的字典缓存
     * 加载所有字典及其字典项到Redis
     */
    void refreshDictCache();
}
