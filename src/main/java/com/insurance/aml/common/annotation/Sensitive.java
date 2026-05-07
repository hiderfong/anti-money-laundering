package com.insurance.aml.common.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.insurance.aml.common.serializer.SensitiveSerializer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感数据脱敏注解
 *
 * 标注在字段上，Jackson 序列化时自动脱敏
 * 支持姓名、身份证、手机号、邮箱、银行卡等类型
 *
 * 示例：
 *   @Sensitive(Sensitive.Type.NAME)
 *   private String customerName;
 *
 *   @Sensitive(Sensitive.Type.ID_CARD)
 *   private String idNumber;
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveSerializer.class)
public @interface Sensitive {

    /**
     * 脱敏类型
     */
    Type value();

    /**
     * 脱敏类型枚举
     */
    enum Type {
        /** 姓名：保留姓，名用* */
        NAME,
        /** 身份证号：前3后4 */
        ID_CARD,
        /** 手机号：前3后4 */
        PHONE,
        /** 邮箱：@前保留首尾 */
        EMAIL,
        /** 银行卡号：后4位 */
        BANK_ACCOUNT,
        /** 地址：保留前6个字符 */
        ADDRESS,
        /** 自定义：保留前prefixLen和后suffixLen个字符 */
        CUSTOM
    }

    /**
     * 自定义脱敏 - 前缀保留长度（仅 CUSTOM 类型生效）
     */
    int prefixLen() default 3;

    /**
     * 自定义脱敏 - 后缀保留长度（仅 CUSTOM 类型生效）
     */
    int suffixLen() default 4;
}
