package com.insurance.aml.module.organization.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 机构档案新增或更新请求。
 */
@Data
public class OrganizationRequest {
    @NotBlank(message = "机构编码不能为空")
    @Size(max = 64, message = "机构编码不能超过64个字符")
    private String orgCode;

    @NotBlank(message = "机构名称不能为空")
    @Size(max = 256, message = "机构名称不能超过256个字符")
    private String orgName;

    @NotBlank(message = "统一社会信用代码不能为空")
    @Size(max = 32, message = "统一社会信用代码不能超过32个字符")
    private String unifiedCreditCode;

    @Size(max = 32, message = "LEI编码不能超过32个字符")
    private String leiCode;

    @NotBlank(message = "机构类型不能为空")
    @Pattern(regexp = "HEAD_OFFICE|BRANCH|OUTLET", message = "机构类型不正确")
    private String orgType;

    private Long parentId;
    private String registeredAddress;
    private String businessAddress;
    private String legalRepresentative;

    @DecimalMin(value = "0", inclusive = true, message = "注册资本不能为负数")
    private BigDecimal registeredCapital;

    private String businessScope;
    private String regulatorName;

    @Pattern(regexp = "ENABLED|DISABLED", message = "机构状态不正确")
    private String status;
}
