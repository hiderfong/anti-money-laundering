package com.insurance.aml.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户状态枚举
 */
@Getter
@AllArgsConstructor
public enum CustomerStatus {

    ACTIVE("ACTIVE", "活跃"),
    INACTIVE("INACTIVE", "停用"),
    FROZEN("FROZEN", "冻结");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    public static CustomerStatus fromCode(String code) {
        for (CustomerStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的客户状态: " + code);
    }
}
