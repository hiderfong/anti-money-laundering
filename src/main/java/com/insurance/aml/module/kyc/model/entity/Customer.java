package com.insurance.aml.module.kyc.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户信息实体
 */
@Data
@TableName("t_customer")
public class Customer extends BaseEntity {

    /**
     * 客户编号
     */
    private String customerNo;

    /**
     * 客户类型：INDIVIDUAL-个人，CORPORATE-企业
     */
    private String customerType;

    /**
     * 客户名称
     */
    private String name;

    /**
     * 英文名称
     */
    private String nameEn;

    /**
     * 性别
     */
    private String gender;

    /**
     * 国籍
     */
    private String nationality;

    /**
     * 民族
     */
    private String ethnicGroup;

    /**
     * 出生日期
     */
    private LocalDate birthDate;

    /**
     * 证件类型
     */
    private String idType;

    /**
     * 证件号码
     */
    private String idNumber;

    /**
     * 证件签发机关
     */
    private String idIssuingAuthority;

    /**
     * 证件有效期
     */
    private LocalDate idExpiryDate;

    /**
     * 地址
     */
    private String address;

    /**
     * 居住地址
     */
    private String residenceAddress;

    /**
     * 手机号码
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 职业
     */
    private String occupation;

    /**
     * 工作单位
     */
    private String employer;

    /**
     * 职位
     */
    private String jobTitle;

    /**
     * 年收入区间
     */
    private String annualIncomeRange;

    /**
     * 税务居民身份
     */
    private String taxResidentStatus;

    /**
     * 统一社会信用代码
     */
    private String unifiedCreditCode;

    /**
     * 企业类型
     */
    private String enterpriseType;

    /**
     * 注册资本
     */
    private BigDecimal registeredCapital;

    /**
     * 经营范围
     */
    private String businessScope;

    /**
     * 法定代表人
     */
    private String legalRepresentative;

    /**
     * 风险等级：LOW-低，MEDIUM-中，HIGH-高
     */
    private String riskLevel = "LOW";

    /**
     * 风险评分
     */
    private Integer riskScore = 0;

    /**
     * 风险更新时间
     */
    private LocalDateTime riskUpdateTime;

    /**
     * 是否为PEP（政治敏感人物）
     */
    private Boolean isPep = false;

    /**
     * PEP类型
     */
    private String pepType;

    /**
     * 是否被制裁
     */
    private Boolean isSanctioned = false;

    /**
     * KYC状态：INCOMPLETE-不完整，COMPLETE-完整，REVIEWING-审核中
     */
    private String kycStatus = "INCOMPLETE";

    /**
     * KYC最近审核时间
     */
    private LocalDateTime kycLastReviewTime;

    /**
     * KYC下次审核时间
     */
    private LocalDateTime kycNextReviewTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 状态：ACTIVE-活跃，INACTIVE-停用，FROZEN-冻结
     */
    private String status = "ACTIVE";
}
