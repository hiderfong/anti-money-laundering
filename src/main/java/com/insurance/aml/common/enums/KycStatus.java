package com.insurance.aml.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * KYC 客户身份识别状态枚举
 */
@Getter
@AllArgsConstructor
public enum KycStatus {

    INCOMPLETE("INCOMPLETE", "未完成"),
    COMPLETE("COMPLETE", "已完成"),
    PENDING_REVIEW("PENDING_REVIEW", "待复核"),
    REJECTED("REJECTED", "已拒绝");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    public static KycStatus fromCode(String code) {
        for (KycStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的KYC状态: " + code);
    }
}
