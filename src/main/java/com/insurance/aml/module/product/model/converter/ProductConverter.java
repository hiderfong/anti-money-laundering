package com.insurance.aml.module.product.model.converter;

import com.insurance.aml.module.product.model.dto.ProductVO;
import com.insurance.aml.module.product.model.entity.Product;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Product 对象转换器
 * 使用 MapStruct 替代 BeanUtils.copyProperties
 *
 * Product 继承 BaseEntity，MapStruct 自动映射 BaseEntity 中的
 * id/createdBy/createdTime/updatedBy/updatedTime 到 ProductVO 对应字段
 */
@Mapper(componentModel = "spring")
public interface ProductConverter {

    /**
     * Product 实体 -> ProductVO
     * 所有同名同类型字段自动映射，包括 BaseEntity 继承的审计字段
     */
    ProductVO toVO(Product product);

    /**
     * Product 实体列表 -> ProductVO 列表
     */
    List<ProductVO> toVOList(List<Product> products);

    /**
     * ProductVO -> Product 实体（反向转换）
     */
    Product toEntity(ProductVO productVO);
}
