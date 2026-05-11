package com.insurance.aml.module.system.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.system.model.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户Mapper接口
 */
@Mapper
public interface SysUserMapper extends BaseMapperX<SysUser> {
}
