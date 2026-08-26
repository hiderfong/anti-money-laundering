package com.insurance.aml.module.organization.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 总公司股权结构信息。个人股东不在此表保存证件号码，减少敏感信息暴露面。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_aml_org_shareholder")
public class OrganizationShareholder extends BaseEntity {
    private Long organizationId;
    private String shareholderName;
    private String shareholderType;
    private String registrationCode;
    private BigDecimal ownershipPercentage;
    private Boolean controllingFlag;
    private String status;
}
