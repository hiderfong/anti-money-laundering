package com.insurance.aml.module.monitoring.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.monitoring.model.entity.RuleExecutionLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 规则执行日志Mapper接口
 */
@Mapper
public interface RuleExecutionLogMapper extends BaseMapperX<RuleExecutionLog> {
}
