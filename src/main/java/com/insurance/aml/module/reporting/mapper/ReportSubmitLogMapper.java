package com.insurance.aml.module.reporting.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.reporting.model.entity.ReportSubmitLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报告提交日志Mapper接口
 */
@Mapper
public interface ReportSubmitLogMapper extends BaseMapperX<ReportSubmitLog> {
}
