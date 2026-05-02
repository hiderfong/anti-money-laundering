package com.insurance.aml.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ==================== 通用状态码 ====================
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "bad request"),
    UNAUTHORIZED(401, "unauthorized"),
    FORBIDDEN(403, "forbidden"),
    NOT_FOUND(404, "not found"),
    INTERNAL_ERROR(500, "internal server error"),

    // ==================== 用户模块 100xxx ====================
    USER_NOT_FOUND(100001, "user not found"),
    USERNAME_EXISTS(100002, "username already exists"),
    PASSWORD_ERROR(100003, "password error"),
    ACCOUNT_LOCKED(100004, "account locked"),
    ACCOUNT_DISABLED(100005, "account disabled"),
    TOKEN_EXPIRED(100006, "token expired"),
    TOKEN_INVALID(100007, "token invalid"),

    // ==================== 客户模块 110xxx ====================
    CUSTOMER_NOT_FOUND(110001, "customer not found"),
    CUSTOMER_ID_EXISTS(110002, "customer id already exists"),
    KYC_INCOMPLETE(110003, "kyc incomplete"),
    RATING_FAILED(110004, "risk rating failed"),

    // ==================== 名单筛查模块 120xxx ====================
    WATCHLIST_IMPORT_ERROR(120001, "watchlist import format error"),
    SCREENING_TIMEOUT(120002, "screening timeout"),
    WHITELIST_EXPIRED(120003, "whitelist expired"),

    // ==================== 规则引擎模块 130xxx ====================
    RULE_CONFIG_INVALID(130001, "rule config invalid"),
    RULE_NOT_FOUND(130002, "rule not found"),
    RULE_EXECUTE_ERROR(130003, "rule execute error"),

    // ==================== 预警模块 140xxx ====================
    ALERT_STATUS_ERROR(140001, "alert status not allowed"),
    ALERT_NOT_FOUND(140002, "alert not found"),

    // ==================== 案例模块 150xxx ====================
    CASE_CLOSED(150001, "case already closed"),
    CASE_NOT_FOUND(150002, "case not found"),

    // ==================== 报送模块 160xxx ====================
    REPORT_VALIDATION_FAIL(160001, "report xml validation failed"),
    REPORT_SUBMIT_FAIL(160002, "report submit failed");

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误消息
     */
    private final String message;
}
