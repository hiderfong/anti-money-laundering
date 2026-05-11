package com.insurance.aml.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 案件状态枚举
 */
@Getter
@AllArgsConstructor
public enum CaseStatus {

    DRAFT("DRAFT", "草稿"),
    INVESTIGATING("INVESTIGATING", "调查中"),
    PENDING_APPROVAL("PENDING_APPROVAL", "待审批"),
    SUBMITTED("SUBMITTED", "已报送"),
    CLOSED("CLOSED", "已关闭");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    public static CaseStatus fromCode(String code) {
        for (CaseStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的案件状态: " + code);
    }
}
