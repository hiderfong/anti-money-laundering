package com.insurance.aml.module.screening.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.screening.model.entity.ScreeningResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 筛查结果 Mapper
 */
@Mapper
public interface ScreeningResultMapper extends BaseMapperX<ScreeningResult> {
}
