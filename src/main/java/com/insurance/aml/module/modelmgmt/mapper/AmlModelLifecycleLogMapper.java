package com.insurance.aml.module.modelmgmt.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.modelmgmt.model.entity.AmlModelLifecycleLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 反洗钱模型生命周期记录 Mapper。
 */
@Mapper
public interface AmlModelLifecycleLogMapper extends BaseMapperX<AmlModelLifecycleLog> {
}
