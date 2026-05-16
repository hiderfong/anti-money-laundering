package com.insurance.aml.module.modelmgmt.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.modelmgmt.model.entity.AmlModel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 反洗钱模型 Mapper。
 */
@Mapper
public interface AmlModelMapper extends BaseMapperX<AmlModel> {
}
