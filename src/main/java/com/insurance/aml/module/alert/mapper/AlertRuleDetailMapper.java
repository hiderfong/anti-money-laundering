package com.insurance.aml.module.alert.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.alert.model.entity.AlertRuleDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预警规则明细Mapper
 */
@Mapper
public interface AlertRuleDetailMapper extends BaseMapperX<AlertRuleDetail> {
}
