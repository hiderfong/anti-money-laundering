package com.insurance.aml.module.kyc.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 客户受益所有人实体
 */
@Data
@TableName("t_customer_beneficial_owner")
public class CustomerBeneficialOwner extends BaseEntity {

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 受益所有人姓名
     */
    private String ownerName;

    /**
     * 证件类型
     */
    private String ownerIdType;

    /**
     * 证件号码
     */
    private String ownerIdNumber;

    /**
     * 国籍
     */
    private String nationality;

    /**
     * 出生日期
     */
    private LocalDate birthDate;

    /**
     * 持股/控制比例
     */
    private BigDecimal ownershipPercentage;

    /**
     * 控制类型：EQUITY-股权控制，CONTROL-实际控制，BOTH-双重控制
     */
    private String controlType;

    /**
     * 控制关系描述
     */
    private String controlDescription;

    /**
     * 与客户关系
     */
    private String relationship;

    /**
     * 状态
     */
    private String status;
}
