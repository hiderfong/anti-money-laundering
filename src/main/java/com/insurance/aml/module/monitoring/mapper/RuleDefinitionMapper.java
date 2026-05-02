package com.insurance.aml.module.monitoring.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.monitoring.model.entity.RuleDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 规则定义Mapper接口
 */
@Mapper
public interface RuleDefinitionMapper extends BaseMapperX<RuleDefinition> {
}
