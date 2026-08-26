package com.insurance.aml.module.organization.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 机构股东维护请求。
 */
@Data
public class OrganizationShareholderRequest {
    @NotBlank(message = "股东名称不能为空")
    private String shareholderName;

    @NotBlank(message = "股东类型不能为空")
    @Pattern(regexp = "INDIVIDUAL|ORGANIZATION", message = "股东类型不正确")
    private String shareholderType;

    private String registrationCode;

    @NotNull(message = "持股比例不能为空")
    @DecimalMin(value = "0.0001", message = "持股比例必须大于0")
    @DecimalMax(value = "100.0000", message = "持股比例不能超过100%")
    private BigDecimal ownershipPercentage;

    private Boolean controllingFlag;

    @Pattern(regexp = "ENABLED|DISABLED", message = "股东状态不正确")
    private String status;
}
