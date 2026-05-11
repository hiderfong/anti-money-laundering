package com.insurance.aml.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解
 * 标注在需要记录审计日志的方法上，配合AuditLogAspect切面实现自动审计
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /**
     * 功能模块
     * 例如：KYC、筛查、案件管理、报告等
     */
    String module();

    /**
     * 操作类型
     * 例如：CREATE、UPDATE、DELETE、QUERY等
     */
    String operationType();

    /**
     * 操作描述
     * 例如：创建客户、更新客户信息等
     */
    String description() default "";
}
