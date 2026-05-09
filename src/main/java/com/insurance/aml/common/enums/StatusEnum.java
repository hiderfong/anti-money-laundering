package com.insurance.aml.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用启用/停用状态枚举
 */
@Getter
@AllArgsConstructor
public enum StatusEnum {

    ACTIVE("ACTIVE", "启用"),
    INACTIVE("INACTIVE", "停用"),
    DISABLED("DISABLED", "已禁用");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    public static StatusEnum fromCode(String code) {
        for (StatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的状态: " + code);
    }
}
