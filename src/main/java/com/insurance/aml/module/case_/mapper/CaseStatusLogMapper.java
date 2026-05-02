package com.insurance.aml.module.case_.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.case_.model.entity.CaseStatusLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 案件状态变更日志 Mapper接口
 */
@Mapper
public interface CaseStatusLogMapper extends BaseMapperX<CaseStatusLog> {
}
