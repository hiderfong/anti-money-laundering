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

    OPEN("OPEN", "待处理"),
    INVESTIGATING("INVESTIGATING", "调查中"),
    SUBMITTED("SUBMITTED", "已提交"),
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
