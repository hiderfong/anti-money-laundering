package com.insurance.aml.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 整改状态枚举
 */
@Getter
@AllArgsConstructor
public enum RectificationStatus {

    OPEN("OPEN", "待整改"),
    IN_PROGRESS("IN_PROGRESS", "整改中"),
    COMPLETED("COMPLETED", "已完成"),
    VERIFIED("VERIFIED", "已验证");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    public static RectificationStatus fromCode(String code) {
        for (RectificationStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的整改状态: " + code);
    }
}
