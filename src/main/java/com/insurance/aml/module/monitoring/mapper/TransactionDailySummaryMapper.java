package com.insurance.aml.module.monitoring.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.monitoring.model.entity.TransactionDailySummary;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易日汇总Mapper接口
 */
@Mapper
public interface TransactionDailySummaryMapper extends BaseMapperX<TransactionDailySummary> {
}
