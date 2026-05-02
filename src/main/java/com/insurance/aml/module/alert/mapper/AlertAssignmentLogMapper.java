package com.insurance.aml.module.alert.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.alert.model.entity.AlertAssignmentLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预警分配日志Mapper
 */
@Mapper
public interface AlertAssignmentLogMapper extends BaseMapperX<AlertAssignmentLog> {
}
