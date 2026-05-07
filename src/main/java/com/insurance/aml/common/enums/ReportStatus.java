package com.insurance.aml.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 报送状态枚举
 */
@Getter
@AllArgsConstructor
public enum ReportStatus {

    DRAFT("DRAFT", "草稿"),
    PENDING("PENDING", "待审批"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回"),
    SUBMITTED("SUBMITTED", "已报送");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    public static ReportStatus fromCode(String code) {
        for (ReportStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的报送状态: " + code);
    }
}
