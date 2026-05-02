package com.insurance.aml.common.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 自定义通用 Mapper 基类
 * 继承 MyBatis-Plus BaseMapper，增加常用查询方法
 * @param <T> 实体类型
 */
public interface BaseMapperX<T> extends BaseMapper<T> {

    /**
     * 分页查询列表（带条件）
     * @param page 分页参数
     * @param queryWrapper 查询条件
     * @return 分页结果
     */
    Page<T> selectPageList(IPage<T> page, @Param("ew") Wrapper<T> queryWrapper);

    /**
     * 根据条件查询列表
     * @param queryWrapper 查询条件
     * @return 查询结果列表
     */
    List<T> selectListByCondition(@Param("ew") Wrapper<T> queryWrapper);
}
