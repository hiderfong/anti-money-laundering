package com.insurance.aml.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 筛查任务状态枚举
 */
@Getter
@AllArgsConstructor
public enum ScreeningStatus {

    PROCESSING("PROCESSING", "处理中"),
    COMPLETED("COMPLETED", "已完成"),
    FAILED("FAILED", "失败");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    public static ScreeningStatus fromCode(String code) {
        for (ScreeningStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的筛查状态: " + code);
    }
}
