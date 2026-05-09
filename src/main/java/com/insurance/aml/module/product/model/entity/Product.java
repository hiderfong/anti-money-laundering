package com.insurance.aml.module.product.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import com.insurance.aml.common.enums.RiskLevel;
import com.insurance.aml.common.enums.StatusEnum;

import lombok.Data;

import java.time.LocalDate;

/**
 * 产品信息实体
 * 用于产品风险评估的基础数据
 */
@Data
@TableName("t_product")
public class Product extends BaseEntity {

    /**
     * 产品编码（唯一）
     */
    private String productCode;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 产品类型：LIFE-人寿险、PROPERTY-财产险、HEALTH-健康险、ACCIDENT-意外险、ANNUITY-年金险
     */
    private String productType;

    /**
     * 产品子类型
     */
    private String productSubType;

    /**
     * 缴费方式：LUMP_SUM-趸交、PERIODIC-期交、FLEXIBLE-灵活缴费
     */
    private String paymentMode;

    /**
     * 是否具有现金价值
     */
    private Boolean hasCashValue = false;

    /**
     * 是否具有投资功能
     */
    private Boolean hasInvestmentFeature = false;

    /**
     * 退保灵活性：LOW-低、MEDIUM-中、HIGH-高
     */
    private String surrenderFlexibility;

    /**
     * 受益人是否可变更
     */
    private Boolean beneficiaryChangeable = true;

    /**
     * 风险等级：LOW-低、MEDIUM-中、HIGH-高
     */
    private String riskLevel = RiskLevel.LOW.getCode();

    /**
     * 风险评分
     */
    private Integer riskScore = 0;

    /**
     * 风险因素说明（JSON文本）
     */
    private String riskFactors;

    /**
     * 状态：ACTIVE-启用、INACTIVE-停用
     */
    private String status = StatusEnum.ACTIVE.getCode();

    /**
     * 生效日期
     */
    private LocalDate effectiveDate;

    /**
     * 失效日期
     */
    private LocalDate expiryDate;
}
