package com.insurance.aml.common.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.service.BaseServiceX;

/**
 * 自定义通用 Service 实现基类
 * 继承 MyBatis-Plus ServiceImpl，实现 BaseServiceX 接口
 * 提供通用分页查询实现
 *
 * @param <M> Mapper 类型
 * @param <T> 实体类型
 */
public abstract class BaseServiceXImpl<M extends BaseMapper<T>, T> extends ServiceImpl<M, T> implements BaseServiceX<T> {

    /**
     * 通用分页查询实现
     * 将 PageQuery 转换为 MyBatis-Plus 的 IPage 对象，执行分页查询
     *
     * @param pageQuery 分页查询参数
     * @param queryWrapper 查询条件
     * @return 分页结果
     */
    @Override
    public IPage<T> pageQuery(PageQuery pageQuery, Wrapper<T> queryWrapper) {
        // 将前端分页参数转换为 MyBatis-Plus 分页对象
        IPage<T> page = pageQuery.toPage();
        // 执行分页查询
        return baseMapper.selectPage(page, queryWrapper);
    }
}
