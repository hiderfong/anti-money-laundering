package com.insurance.aml.module.kyc.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 客户创建请求
 */
@Data
@Schema(description = "客户创建请求")
public class CustomerCreateRequest {

    @NotBlank(message = "客户类型不能为空")
    @Schema(description = "客户类型：INDIVIDUAL-个人，CORPORATE-企业", required = true)
    private String customerType;

    @NotBlank(message = "客户名称不能为空")
    @Schema(description = "客户名称", required = true)
    private String name;

    @Schema(description = "英文名称")
    private String nameEn;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "国籍")
    private String nationality;

    @Schema(description = "出生日期")
    private LocalDate birthDate;

    @Schema(description = "证件类型")
    private String idType;

    @Schema(description = "证件号码")
    private String idNumber;

    @Schema(description = "证件签发机关")
    private String idIssuingAuthority;

    @Schema(description = "证件有效期")
    private LocalDate idExpiryDate;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "居住地址")
    private String residenceAddress;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "职业")
    private String occupation;

    @Schema(description = "工作单位")
    private String employer;

    @Schema(description = "职位")
    private String jobTitle;

    @Schema(description = "年收入区间")
    private String annualIncomeRange;

    @Schema(description = "税务居民身份")
    private String taxResidentStatus;

    @Schema(description = "统一社会信用代码")
    private String unifiedCreditCode;

    @Schema(description = "企业类型")
    private String enterpriseType;

    @Schema(description = "注册资本")
    private BigDecimal registeredCapital;

    @Schema(description = "经营范围")
    private String businessScope;

    @Schema(description = "法定代表人")
    private String legalRepresentative;
}
