package com.insurance.aml.module.reporting.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.reporting.model.entity.LargeTxnReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 大额交易报告Mapper接口
 */
@Mapper
public interface LargeTxnReportMapper extends BaseMapperX<LargeTxnReport> {
}
