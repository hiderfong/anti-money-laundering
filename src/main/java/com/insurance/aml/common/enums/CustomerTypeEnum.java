package com.insurance.aml.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户类型枚举
 */
@Getter
@AllArgsConstructor
public enum CustomerTypeEnum {

    /** 个人客户 */
    INDIVIDUAL("个人"),

    /** 法人客户 */
    CORPORATE("法人");

    /**
     * 类型标签
     */
    private final String label;
}
