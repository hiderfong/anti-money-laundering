package com.insurance.aml.module.casemgmt.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.casemgmt.model.entity.Case;
import org.apache.ibatis.annotations.Mapper;

/**
 * 案件 Mapper接口
 */
@Mapper
public interface CaseMapper extends BaseMapperX<Case> {
}
