package com.insurance.aml.module.screening.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.screening.model.entity.ScreeningRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 筛查请求 Mapper
 */
@Mapper
public interface ScreeningRequestMapper extends BaseMapperX<ScreeningRequest> {
}
