package com.insurance.aml.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 提交状态枚举（用于报送日志、任务执行记录等）
 */
@Getter
@AllArgsConstructor
public enum SubmitStatus {

    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败"),
    PENDING("PENDING", "待处理");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    public static SubmitStatus fromCode(String code) {
        for (SubmitStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的提交状态: " + code);
    }
}
