package com.insurance.aml.module.organization.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 反洗钱机构档案，覆盖总公司、分支机构和营业网点。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_aml_organization")
public class AmlOrganization extends BaseEntity {
    private String orgCode;
    private String orgName;
    private String unifiedCreditCode;
    private String leiCode;
    private String orgType;
    private Long parentId;
    private String registeredAddress;
    private String businessAddress;
    private String legalRepresentative;
    private BigDecimal registeredCapital;
    private String businessScope;
    private String regulatorName;
    private String status;
    private String registrationStatus;
}
