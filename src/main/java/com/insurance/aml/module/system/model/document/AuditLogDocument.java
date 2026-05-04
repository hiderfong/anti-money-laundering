package com.insurance.aml.module.system.model.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志 Elasticsearch 文档
 * 用于全文检索审计日志，支持对 detail、errorMessage 等文本字段的模糊搜索
 */
@Data
@Document(indexName = "audit_log")
public class AuditLogDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（与MySQL主键一致）
     */
    @Id
    private Long id;

    /**
     * 链路追踪ID
     */
    @Field(type = FieldType.Keyword)
    private String traceId;

    /**
     * 操作用户ID
     */
    @Field(type = FieldType.Long)
    private Long userId;

    /**
     * 操作用户名
     */
    @Field(type = FieldType.Text, analyzer = "standard", fielddata = true)
    private String username;

    /**
     * 操作类型
     */
    @Field(type = FieldType.Keyword)
    private String operationType;

    /**
     * 所属模块
     */
    @Field(type = FieldType.Keyword)
    private String module;

    /**
     * 操作目标类型
     */
    @Field(type = FieldType.Keyword)
    private String targetType;

    /**
     * 操作目标ID
     */
    @Field(type = FieldType.Keyword)
    private String targetId;

    /**
     * 操作详情（支持全文检索）
     */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String detail;

    /**
     * 客户端IP地址
     */
    @Field(type = FieldType.Keyword)
    private String ipAddress;

    /**
     * 用户代理信息
     */
    @Field(type = FieldType.Keyword)
    private String userAgent;

    /**
     * 请求URI
     */
    @Field(type = FieldType.Keyword)
    private String requestUri;

    /**
     * 请求方法
     */
    @Field(type = FieldType.Keyword)
    private String requestMethod;

    /**
     * 响应状态码
     */
    @Field(type = FieldType.Integer)
    private int responseCode;

    /**
     * 请求耗时（毫秒）
     */
    @Field(type = FieldType.Long)
    private Long durationMs;

    /**
     * 错误信息（支持全文检索）
     */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String errorMessage;

    /**
     * 创建时间
     */
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdTime;
}
