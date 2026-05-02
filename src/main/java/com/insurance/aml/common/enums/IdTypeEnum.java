package com.insurance.aml.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 证件类型枚举
 */
@Getter
@AllArgsConstructor
public enum IdTypeEnum {

    /** 身份证 */
    IDCARD("身份证", "01"),

    /** 护照 */
    PASSPORT("护照", "02"),

    /** 港澳台通行证 */
    HK_MACAO_TW("港澳台通行证", "03"),

    /** 军官证 */
    MILITARY("军官证", "04"),

    /** 其他 */
    OTHER("其他", "99");

    /**
     * 证件类型标签
     */
    private final String label;

    /**
     * 证件类型编码
     */
    private final String code;
}
