package com.insurance.aml.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 *
 * 基于 Redis + Lua 滑动窗口算法实现
 * 支持按 IP、用户、全局三种维度限流
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流 key 前缀
     */
    String key() default "";

    /**
     * 时间窗口内最大请求数
     */
    int maxRequests() default 20;

    /**
     * 时间窗口（秒）
     */
    int windowSeconds() default 1;

    /**
     * 限流维度
     */
    Dimension dimension() default Dimension.IP;

    /**
     * 超限提示信息
     */
    String message() default "请求过于频繁，请稍后再试";

    /**
     * 限流维度枚举
     */
    enum Dimension {
        /** 按 IP 限流 */
        IP,
        /** 按用户限流 */
        USER,
        /** 全局限流 */
        GLOBAL
    }
}
