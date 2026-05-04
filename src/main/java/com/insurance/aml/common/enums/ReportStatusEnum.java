package com.insurance.aml.common.enums;

/**
 * 报告状态枚举
 */
public enum ReportStatusEnum {

    PENDING("PENDING", "待审核", "报告已生成，等待审核"),
    APPROVED("APPROVED", "已通过", "报告审核通过"),
    SUBMITTED("SUBMITTED", "已报送", "报告已向监管机构报送"),
    REJECTED("REJECTED", "已驳回", "报告审核未通过，已驳回修改");

    private final String code;
    private final String label;
    private final String description;

    ReportStatusEnum(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code查找枚举，未找到返回null
     */
    public static ReportStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ReportStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 根据code查找枚举，未找到抛异常
     */
    public static ReportStatusEnum of(String code) {
        ReportStatusEnum e = fromCode(code);
        if (e == null) {
            throw new IllegalArgumentException("Unknown ReportStatusEnum code: " + code);
        }
        return e;
    }
}
