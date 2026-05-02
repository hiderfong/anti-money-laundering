package com.insurance.aml.common.service;

import com.insurance.aml.common.config.AsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 审计日志写入服务
 * 异步写入操作日志到数据库，不影响主业务性能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
        INSERT INTO t_audit_log 
        (trace_id, user_id, username, operation_type, module, target_type, target_id, 
         detail, ip_address, user_agent, request_uri, request_method, response_code, duration_ms, error_message)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

    /**
     * 异步写入审计日志
     */
    @Async(AsyncConfig.TASK_EXECUTOR)
    public void writeAuditLog(String traceId, Long userId, String username,
                               String operationType, String module,
                               String targetType, String targetId,
                               String detail, String ipAddress, String userAgent,
                               String requestUri, String requestMethod,
                               Integer responseCode, Long durationMs, String errorMessage) {
        try {
            jdbcTemplate.update(INSERT_SQL,
                    traceId, userId, username, operationType, module,
                    targetType, targetId, detail, ipAddress, userAgent,
                    requestUri, requestMethod, responseCode, durationMs, errorMessage);
        } catch (Exception e) {
            // 审计日志写入失败不应影响业务
            log.error("审计日志写入失败: module={}, operation={}, target={}", module, operationType, targetId, e);
        }
    }
}
