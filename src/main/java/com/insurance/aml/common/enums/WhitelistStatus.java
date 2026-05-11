package com.insurance.aml.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 白名单状态枚举
 */
@Getter
@AllArgsConstructor
public enum WhitelistStatus {

    ACTIVE("ACTIVE", "有效"),
    EXPIRED("EXPIRED", "已过期"),
    REVOKED("REVOKED", "已撤销");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    public static WhitelistStatus fromCode(String code) {
        for (WhitelistStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的白名单状态: " + code);
    }
}
