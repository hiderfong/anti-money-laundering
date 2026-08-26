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
    TOO_MANY_REQUESTS(429, "too many requests"),
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
    REPORT_SUBMIT_FAIL(160002, "report submit failed"),
    REPORT_SUBMISSION_NOT_FOUND(160003, "regulatory submission not found"),
    REPORT_STATUS_INVALID(160004, "report status does not allow submission"),
    REPORT_ALREADY_ACCEPTED(160005, "report already accepted by regulator"),
    REPORT_RESUBMIT_REQUIRED(160006, "existing submission must be resubmitted"),
    REPORT_RESUBMIT_NOT_ALLOWED(160007, "submission status does not allow resubmission"),
    REPORT_RECEIPT_NOT_PENDING(160008, "regulatory receipt is not pending"),
    REPORT_RECEIPT_INVALID(160009, "regulatory receipt is invalid"),
    REPORT_CONNECTOR_NOT_CONFIGURED(160010, "regulatory connector is not configured"),
    REPORT_GATEWAY_NOT_FOUND(160011, "regulatory gateway adapter not found"),
    REPORT_SIGNATURE_FAIL(160012, "regulatory payload signing failed"),
    REPORT_TYPE_UNSUPPORTED(160013, "regulatory report type unsupported"),

    // ==================== 机构治理模块 170xxx ====================
    ORGANIZATION_NOT_FOUND(170001, "organization not found"),
    ORGANIZATION_CODE_EXISTS(170002, "organization code already exists"),
    ORGANIZATION_CREDIT_CODE_EXISTS(170003, "organization credit code already exists"),
    ORGANIZATION_SCOPE_FORBIDDEN(170004, "organization data scope forbidden"),
    ORGANIZATION_REGISTRATION_STATUS_ERROR(170005, "organization registration status not allowed"),
    ORGANIZATION_REGISTRATION_INCOMPLETE(170006, "organization registration information incomplete"),

    // ==================== 外部集成模块 180xxx ====================
    INTEGRATION_CONNECTOR_NOT_FOUND(180001, "integration connector not found"),
    INTEGRATION_CONNECTOR_CODE_EXISTS(180002, "integration connector code already exists"),
    INTEGRATION_CONNECTOR_DISABLED(180003, "integration connector disabled"),
    INTEGRATION_ADAPTER_NOT_FOUND(180004, "integration adapter not found"),
    INTEGRATION_JOB_NOT_FOUND(180005, "integration job not found"),
    INTEGRATION_JOB_CODE_EXISTS(180006, "integration job code already exists"),
    INTEGRATION_JOB_DISABLED(180007, "integration job disabled"),
    INTEGRATION_JOB_RUNNING(180008, "integration job is running"),
    INTEGRATION_RUN_NOT_FOUND(180009, "integration run not found"),
    INTEGRATION_RUN_NOT_RETRYABLE(180010, "integration run is not retryable"),
    INTEGRATION_CONFIG_INVALID(180011, "integration configuration invalid");

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误消息
     */
    private final String message;
}
