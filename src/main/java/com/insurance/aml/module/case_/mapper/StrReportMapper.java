package com.insurance.aml.module.case_.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.case_.model.entity.StrReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 可疑交易报告 Mapper接口
 */
@Mapper
public interface StrReportMapper extends BaseMapperX<StrReport> {
}
