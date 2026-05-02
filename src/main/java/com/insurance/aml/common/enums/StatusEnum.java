package com.insurance.aml.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用状态枚举
 */
@Getter
@AllArgsConstructor
public enum StatusEnum {

    /** 启用 */
    ACTIVE("启用"),

    /** 停用 */
    INACTIVE("停用"),

    /** 已禁用 */
    DISABLED("已禁用");

    /**
     * 状态标签
     */
    private final String label;
}
