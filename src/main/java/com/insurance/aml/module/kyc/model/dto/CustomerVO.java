package com.insurance.aml.module.kyc.model.dto;

import com.insurance.aml.common.annotation.MaskField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户详情视图对象
 */
@Data
@Schema(description = "客户详情视图对象")
public class CustomerVO {

    @Schema(description = "客户ID")
    private Long id;

    @Schema(description = "客户编号")
    private String customerNo;

    @Schema(description = "客户类型")
    private String customerType;

    @MaskField(MaskField.MaskType.NAME)
    @Schema(description = "客户名称")
    private String name;

    @Schema(description = "英文名称")
    private String nameEn;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "国籍")
    private String nationality;

    @Schema(description = "民族")
    private String ethnicGroup;

    @Schema(description = "出生日期")
    private LocalDate birthDate;

    @Schema(description = "证件类型")
    private String idType;

    @MaskField(MaskField.MaskType.ID_NUMBER)
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

    @MaskField(MaskField.MaskType.PHONE)
    @Schema(description = "手机号码")
    private String phone;

    @MaskField(MaskField.MaskType.EMAIL)
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

    @Schema(description = "风险等级")
    private String riskLevel;

    @Schema(description = "风险评分")
    private Integer riskScore;

    @Schema(description = "风险更新时间")
    private LocalDateTime riskUpdateTime;

    @Schema(description = "是否PEP")
    private Boolean isPep;

    @Schema(description = "PEP类型")
    private String pepType;

    @Schema(description = "是否被制裁")
    private Boolean isSanctioned;

    @Schema(description = "KYC状态")
    private String kycStatus;

    @Schema(description = "KYC最近审核时间")
    private LocalDateTime kycLastReviewTime;

    @Schema(description = "KYC下次审核时间")
    private LocalDateTime kycNextReviewTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    @Schema(description = "受益所有人列表")
    private List<CustomerBeneficialOwnerVO> beneficialOwners;
}
