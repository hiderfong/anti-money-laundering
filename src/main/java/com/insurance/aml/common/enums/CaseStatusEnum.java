package com.insurance.aml.common.enums;

/**
 * 案件状态枚举
 */
public enum CaseStatusEnum {

    DRAFT("DRAFT", "草稿", "案件尚未提交，处于草稿阶段"),
    INVESTIGATING("INVESTIGATING", "调查中", "案件正在调查处理"),
    PENDING_APPROVAL("PENDING_APPROVAL", "待审批", "案件已提交，等待上级审批"),
    SUBMITTED("SUBMITTED", "已报送", "案件已向监管机构报送"),
    CLOSED("CLOSED", "已关闭", "案件已结案");

    private final String code;
    private final String label;
    private final String description;

    CaseStatusEnum(String code, String label, String description) {
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
    public static CaseStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CaseStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 根据code查找枚举，未找到抛异常
     */
    public static CaseStatusEnum of(String code) {
        CaseStatusEnum e = fromCode(code);
        if (e == null) {
            throw new IllegalArgumentException("Unknown CaseStatusEnum code: " + code);
        }
        return e;
    }
}
