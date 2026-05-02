package com.insurance.aml.module.alert.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.alert.model.entity.Alert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预警Mapper
 */
@Mapper
public interface AlertMapper extends BaseMapperX<Alert> {
}
