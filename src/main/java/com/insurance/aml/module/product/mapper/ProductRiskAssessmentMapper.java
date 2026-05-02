package com.insurance.aml.module.product.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.product.model.entity.ProductRiskAssessment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品风险评估记录 Mapper 接口
 */
@Mapper
public interface ProductRiskAssessmentMapper extends BaseMapperX<ProductRiskAssessment> {
}
