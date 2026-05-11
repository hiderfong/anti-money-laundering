package com.insurance.aml.common.enums;

/**
 * 评估状态枚举
 */
public enum AssessmentStatusEnum {

    CREATED("CREATED", "已创建", "评估已创建，尚未评分"),
    SCORED("SCORED", "已评分", "评估已完成评分"),
    COMPLETED("COMPLETED", "已完成", "评估流程已完成"),
    APPROVED("APPROVED", "已批准", "评估结果已批准"),
    REJECTED("REJECTED", "已驳回", "评估结果未通过，已驳回");

    private final String code;
    private final String label;
    private final String description;

    AssessmentStatusEnum(String code, String label, String description) {
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
    public static AssessmentStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AssessmentStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 根据code查找枚举，未找到抛异常
     */
    public static AssessmentStatusEnum of(String code) {
        AssessmentStatusEnum e = fromCode(code);
        if (e == null) {
            throw new IllegalArgumentException("Unknown AssessmentStatusEnum code: " + code);
        }
        return e;
    }
}
