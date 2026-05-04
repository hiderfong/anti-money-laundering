package com.insurance.aml.module.system.repository;

import com.insurance.aml.module.system.model.document.AuditLogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/**
 * 审计日志 Elasticsearch Repository
 * 提供基于 Spring Data Elasticsearch 的审计日志文档操作
 */
public interface AuditLogElasticsearchRepository extends ElasticsearchRepository<AuditLogDocument, Long> {

    /**
     * 按模块查询
     */
    List<AuditLogDocument> findByModule(String module);

    /**
     * 按操作类型查询
     */
    List<AuditLogDocument> findByOperationType(String operationType);

    /**
     * 按用户名查询
     */
    List<AuditLogDocument> findByUsername(String username);
}
