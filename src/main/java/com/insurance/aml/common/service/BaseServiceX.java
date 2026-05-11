package com.insurance.aml.common.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.insurance.aml.common.result.PageQuery;

/**
 * 自定义通用 Service 基类接口
 * 继承 MyBatis-Plus IService，增加分页查询方法
 * @param <T> 实体类型
 */
public interface BaseServiceX<T> extends IService<T> {

    /**
     * 通用分页查询
     * @param pageQuery 分页查询参数
     * @param queryWrapper 查询条件
     * @return 分页结果
     */
    IPage<T> pageQuery(PageQuery pageQuery, Wrapper<T> queryWrapper);
}
