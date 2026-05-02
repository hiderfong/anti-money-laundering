package com.insurance.aml.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段脱敏注解
 * 标注在需要脱敏处理的字段上，配合MaskAspect切面实现自动脱敏
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MaskField {

    /**
     * 脱敏类型
     */
    MaskType value();

    /**
     * 脱敏类型枚举
     */
    enum MaskType {
        /** 姓名脱敏 */
        NAME,
        /** 身份证号脱敏 */
        ID_NUMBER,
        /** 手机号脱敏 */
        PHONE,
        /** 邮箱脱敏 */
        EMAIL,
        /** 银行卡号脱敏 */
        BANK_ACCOUNT
    }
}
