package com.insurance.aml.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 预警状态枚举
 */
@Getter
@AllArgsConstructor
public enum AlertStatus {

    NEW("NEW", "新建"),
    ASSIGNED("ASSIGNED", "已分配"),
    PROCESSING("PROCESSING", "处理中"),
    CONFIRMED("CONFIRMED", "已确认"),
    EXCLUDED("EXCLUDED", "已排除"),
    ESCALATED("ESCALATED", "已升级");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    public static AlertStatus fromCode(String code) {
        for (AlertStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的预警状态: " + code);
    }
}
