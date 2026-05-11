package com.insurance.aml.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 预警处理结果枚举
 */
@Getter
@AllArgsConstructor
public enum AlertProcessResult {

    CONFIRMED_SUSPICIOUS("CONFIRMED_SUSPICIOUS", "确认可疑"),
    EXCLUDED("EXCLUDED", "排除"),
    ESCALATED("ESCALATED", "升级");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    public static AlertProcessResult fromCode(String code) {
        for (AlertProcessResult result : values()) {
            if (result.code.equals(code)) {
                return result;
            }
        }
        throw new IllegalArgumentException("未知的预警处理结果: " + code);
    }
}
