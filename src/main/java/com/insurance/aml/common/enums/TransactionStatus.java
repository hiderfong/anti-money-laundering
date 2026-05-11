package com.insurance.aml.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易状态枚举
 */
@Getter
@AllArgsConstructor
public enum TransactionStatus {

    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败"),
    PENDING("PENDING", "处理中");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    public static TransactionStatus fromCode(String code) {
        for (TransactionStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的交易状态: " + code);
    }
}
