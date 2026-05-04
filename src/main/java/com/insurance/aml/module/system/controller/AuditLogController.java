package com.insurance.aml.module.system.controller;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.system.model.dto.AuditLogQueryRequest;
import com.insurance.aml.module.system.model.dto.AuditLogSearchRequest;
import com.insurance.aml.module.system.model.dto.AuditLogVO;
import com.insurance.aml.module.system.model.document.AuditLogDocument;
import com.insurance.aml.module.system.service.AuditLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.DateRangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审计日志控制器
 * 提供审计日志查询、导出和ES全文检索接口
 */
@Slf4j
@RestController
@RequestMapping("/system/audit-logs")
@RequiredArgsConstructor
@Tag(name = "审计日志", description = "审计日志查询相关接口")
public class AuditLogController {
    private final AuditLogQueryService auditLogQueryService;
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 分页查询审计日志（MySQL）
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询日志", description = "支持按用户ID、用户名、操作类型、模块、时间范围筛选")
    public Result<PageResult<AuditLogVO>> pageQueryLogs(AuditLogQueryRequest req) {
        log.debug("分页查询审计日志");
        PageResult<AuditLogVO> result = auditLogQueryService.pageQueryLogs(req);
        return Result.success(result);
    }

    /**
     * 获取审计日志详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "日志详情", description = "获取指定审计日志的详细信息")
    public Result<AuditLogVO> getLogDetail(
            @Parameter(description = "日志ID") @PathVariable Long id) {
        AuditLogVO vo = auditLogQueryService.getLogDetail(id);
        return Result.success(vo);
    }

    /**
     * 导出审计日志为CSV文件
     */
    @GetMapping("/export")
    @Operation(summary = "导出日志", description = "将审计日志导出为CSV文件下载")
    public ResponseEntity<byte[]> exportLogs(AuditLogQueryRequest req) {
        log.info("导出审计日志CSV请求");
        byte[] csvBytes = auditLogQueryService.exportLogs(req);

        String filename = URLEncoder.encode("审计日志.csv", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(csvBytes.length)
                .body(csvBytes);
    }

    /**
     * ES 全文检索审计日志
     * 支持对 detail、errorMessage、username 等文本字段的模糊搜索
     * 同时支持按 module、operationType、userId、时间范围过滤
     */
    @PostMapping("/search")
    @Operation(summary = "全文检索日志", description = "基于Elasticsearch的全文检索，支持关键词搜索和多条件过滤")
    public Result<PageResult<AuditLogDocument>> fullTextSearch(@RequestBody AuditLogSearchRequest req) {
        log.info("ES全文检索审计日志: keyword={}, module={}, operationType={}",
                req.getKeyword(), req.getModule(), req.getOperationType());

        if (elasticsearchOperations == null) {
            return Result.fail(503, "Elasticsearch search is not enabled");
        }

        // 构建 bool query
        var boolQuery = co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.bool();

        // 全文检索关键词：搜索 detail、errorMessage、username
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            var multiMatchQuery = co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders.multiMatch()
                    .fields("detail", "errorMessage", "username")
                    .query(req.getKeyword())
                    .fuzziness("AUTO")
                    .build();
            boolQuery.must(multiMatchQuery._toQuery());
        }

        // 精确过滤条件
        if (req.getModule() != null && !req.getModule().isBlank()) {
            boolQuery.filter(co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
                    .term().field("module").value(req.getModule()).build()._toQuery());
        }
        if (req.getOperationType() != null && !req.getOperationType().isBlank()) {
            boolQuery.filter(co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
                    .term().field("operationType").value(req.getOperationType()).build()._toQuery());
        }
        if (req.getUserId() != null) {
            boolQuery.filter(co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
                    .term().field("userId").value(req.getUserId()).build()._toQuery());
        }
        if (req.getRequestUri() != null && !req.getRequestUri().isBlank()) {
            boolQuery.filter(co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
                    .term().field("requestUri").value(req.getRequestUri()).build()._toQuery());
        }

        // 时间范围过滤
        if (req.getStartTime() != null || req.getEndTime() != null) {
            DateRangeQuery.Builder dateRangeBuilder = new DateRangeQuery.Builder().field("createdTime");
            if (req.getStartTime() != null) {
                dateRangeBuilder.gte(req.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            if (req.getEndTime() != null) {
                dateRangeBuilder.lte(req.getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            RangeQuery rangeQuery = RangeQuery.of(r -> r.date(dateRangeBuilder.build()));
            boolQuery.filter(rangeQuery._toQuery());
        }

        // 构建 NativeQuery
        int from = (req.getPage() - 1) * req.getSize();
        NativeQuery searchQuery = new NativeQueryBuilder()
                .withQuery(boolQuery.build()._toQuery())
                .withPageable(PageRequest.of(from / req.getSize(), req.getSize()))
                .withSort(Sort.by(Sort.Direction.DESC, "createdTime"))
                .build();

        SearchHits<AuditLogDocument> searchHits = elasticsearchOperations.search(
                searchQuery, AuditLogDocument.class);

        List<AuditLogDocument> documents = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        long totalHits = searchHits.getTotalHits();
        int totalPages = (int) Math.ceil((double) totalHits / req.getSize());

        PageResult<AuditLogDocument> result = PageResult.<AuditLogDocument>builder()
                .total(totalHits)
                .list(documents)
                .page(req.getPage())
                .size(req.getSize())
                .totalPages(totalPages)
                .build();

        log.info("ES全文检索完成: totalHits={}, 返回{}条", totalHits, documents.size());
        return Result.success(result);
    }
}
