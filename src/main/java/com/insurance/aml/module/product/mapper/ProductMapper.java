package com.insurance.aml.module.product.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.product.model.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品 Mapper 接口
 */
@Mapper
public interface ProductMapper extends BaseMapperX<Product> {
}
