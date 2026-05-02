package com.insurance.aml.module.kyc.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.kyc.model.entity.CustomerRiskRatingLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户风险评级变更日志Mapper
 */
@Mapper
public interface CustomerRiskRatingLogMapper extends BaseMapperX<CustomerRiskRatingLog> {
}
