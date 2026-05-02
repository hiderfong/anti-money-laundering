package com.insurance.aml.module.system.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.system.model.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志Mapper接口
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapperX<SysAuditLog> {
}
