package com.insurance.aml.common.service;

import com.insurance.aml.common.config.AsyncConfig;
import com.insurance.aml.module.system.model.document.AuditLogDocument;
import com.insurance.aml.module.system.repository.AuditLogElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 审计日志写入服务
 * 异步写入操作日志到 MySQL + Elasticsearch，不影响主业务性能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<AuditLogElasticsearchRepository> auditLogElasticsearchRepositoryProvider;

    private static final String INSERT_SQL = """
        INSERT INTO t_audit_log 
        (trace_id, user_id, username, operation_type, module, target_type, target_id, 
         detail, ip_address, user_agent, request_uri, request_method, response_code, duration_ms, error_message)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

    /**
     * 异步写入审计日志（MySQL + Elasticsearch 双写）
     */
    @Async(AsyncConfig.TASK_EXECUTOR)
    public void writeAuditLog(String traceId, Long userId, String username,
                               String operationType, String module,
                               String targetType, String targetId,
                               String detail, String ipAddress, String userAgent,
                               String requestUri, String requestMethod,
                               Integer responseCode, Long durationMs, String errorMessage) {
        // 1. 写入 MySQL
        Long mysqlId = null;
        try {
            jdbcTemplate.update(INSERT_SQL,
                    traceId, userId, username, operationType, module,
                    targetType, targetId, detail, ipAddress, userAgent,
                    requestUri, requestMethod, responseCode, durationMs, errorMessage);
            // 获取刚插入的ID（用于ES文档主键一致）
            mysqlId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } catch (Exception e) {
            log.error("审计日志MySQL写入失败: module={}, operation={}, target={}", module, operationType, targetId, e);
        }

        // 2. 异步写入 Elasticsearch（全文检索）
        try {
            AuditLogElasticsearchRepository repository = auditLogElasticsearchRepositoryProvider.getIfAvailable();
            if (repository == null) {
                log.debug("审计日志ES仓库未启用，跳过ES写入: module={}, operation={}, traceId={}",
                        module, operationType, traceId);
                return;
            }

            AuditLogDocument doc = buildDocument(mysqlId, traceId, userId, username,
                    operationType, module, targetType, targetId,
                    detail, ipAddress, userAgent, requestUri, requestMethod,
                    responseCode, durationMs, errorMessage);
            repository.save(doc);
        } catch (Exception e) {
            // ES写入失败不影响业务，仅记录警告
            log.warn("审计日志ES写入失败，不影响主业务: module={}, operation={}, traceId={}",
                    module, operationType, traceId, e);
        }
    }

    /**
     * 构建 ES 文档对象
     */
    private AuditLogDocument buildDocument(Long id, String traceId, Long userId, String username,
                                            String operationType, String module,
                                            String targetType, String targetId,
                                            String detail, String ipAddress, String userAgent,
                                            String requestUri, String requestMethod,
                                            Integer responseCode, Long durationMs, String errorMessage) {
        AuditLogDocument doc = new AuditLogDocument();
        doc.setId(id);
        doc.setTraceId(traceId);
        doc.setUserId(userId);
        doc.setUsername(username);
        doc.setOperationType(operationType);
        doc.setModule(module);
        doc.setTargetType(targetType);
        doc.setTargetId(targetId);
        doc.setDetail(detail);
        doc.setIpAddress(ipAddress);
        doc.setUserAgent(userAgent);
        doc.setRequestUri(requestUri);
        doc.setRequestMethod(requestMethod);
        doc.setResponseCode(responseCode != null ? responseCode : 0);
        doc.setDurationMs(durationMs);
        doc.setErrorMessage(errorMessage);
        doc.setCreatedTime(LocalDateTime.now());
        return doc;
    }
}
