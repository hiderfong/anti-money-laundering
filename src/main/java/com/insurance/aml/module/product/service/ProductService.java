package com.insurance.aml.module.product.service;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.product.model.dto.*;
import com.insurance.aml.module.product.model.entity.Product;
import com.insurance.aml.module.product.model.entity.ProductRiskAssessment;

import java.util.List;

/**
 * 产品管理服务接口
 */
public interface ProductService {

    /**
     * 创建产品
     * @param req 创建请求
     * @return 产品实体
     */
    Product createProduct(ProductCreateRequest req);

    /**
     * 更新产品信息
     * @param id 产品ID
     * @param req 更新请求
     * @return 产品实体
     */
    Product updateProduct(Long id, ProductCreateRequest req);

    /**
     * 分页查询产品
     * @param req 查询请求
     * @return 分页结果
     */
    PageResult<ProductVO> pageQueryProducts(ProductQueryRequest req);

    /**
     * 获取产品详情
     * @param id 产品ID
     * @return 产品VO
     */
    ProductVO getProductDetail(Long id);

    /**
     * 产品风险评估
     * @param req 评估请求
     * @return 评估记录
     */
    ProductRiskAssessment assessProductRisk(ProductRiskAssessRequest req);

    /**
     * 获取产品评估历史
     * @param productId 产品ID
     * @return 评估记录列表
     */
    List<ProductRiskAssessment> getAssessmentHistory(Long productId);
}
