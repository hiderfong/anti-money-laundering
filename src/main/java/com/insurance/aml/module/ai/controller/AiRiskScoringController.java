package com.insurance.aml.module.ai.controller;

import com.insurance.aml.common.result.Result;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.ai.model.dto.AiRiskModelStatusVO;
import com.insurance.aml.module.ai.model.dto.AiRiskTrainingResultVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolItemVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolOverviewVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolQueryRequest;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewRequest;
import com.insurance.aml.module.ai.model.dto.AiRiskScoreRecordVO;
import com.insurance.aml.module.ai.model.dto.AiRiskScoreVO;
import com.insurance.aml.module.ai.model.dto.ModelTrainingStatusVO;
import com.insurance.aml.module.ai.service.AiRiskScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * AI辅助反洗钱风险评分接口。
 */
@Slf4j
@RestController
@RequestMapping("/ai/risk")
@RequiredArgsConstructor
@Tag(name = "AI风险识别", description = "客户、交易、预警的AI辅助反洗钱风险评分")
public class AiRiskScoringController {

    private final AiRiskScoringService aiRiskScoringService;

    @GetMapping("/model-status")
    @Operation(summary = "AI风险评分模型状态")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view') or hasAuthority('monitoring:view')")
    public Result<AiRiskModelStatusVO> getModelStatus() {
        return Result.success(aiRiskScoringService.getModelStatus());
    }

    @PostMapping("/model/retrain")
    @Operation(summary = "触发监督模型重训练")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:manage')")
    public Result<AiRiskTrainingResultVO> retrainModel() {
        return Result.success(aiRiskScoringService.retrainModel());
    }

    @GetMapping("/model/training-status")
    @Operation(summary = "查询监督模型训练状态")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view') or hasAuthority('monitoring:view')")
    public Result<AiRiskTrainingResultVO> trainingStatus() {
        return Result.success(aiRiskScoringService.trainingStatus());
    }

    @GetMapping("/models/training")
    @Operation(summary = "列出所有可训练模型的训练状态")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view') or hasAuthority('monitoring:view')")
    public Result<List<ModelTrainingStatusVO>> listTrainableModels() {
        return Result.success(aiRiskScoringService.listTrainableModels());
    }

    @PostMapping("/models/training/{modelKey}/retrain")
    @Operation(summary = "按模型键触发训练")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:manage')")
    public Result<ModelTrainingStatusVO> retrainModelByKey(
            @PathVariable @Parameter(description = "模型键: supervised | anomaly") String modelKey) {
        return Result.success(aiRiskScoringService.retrainModelByKey(modelKey));
    }

    @GetMapping("/review-pool/overview")
    @Operation(summary = "AI评分监控与待复核池概览")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view') or hasAuthority('monitoring:view')")
    public Result<AiRiskReviewPoolOverviewVO> reviewPoolOverview() {
        return Result.success(aiRiskScoringService.reviewPoolOverview());
    }

    @GetMapping("/review-pool")
    @Operation(summary = "AI评分待复核池")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view') or hasAuthority('monitoring:view')")
    public Result<PageResult<AiRiskReviewPoolItemVO>> pageReviewPool(AiRiskReviewPoolQueryRequest request) {
        return Result.success(aiRiskScoringService.pageReviewPool(request));
    }

    @PostMapping("/review-pool/{recordId}/review")
    @Operation(summary = "登记AI评分人工复核结果")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:manage') or hasAuthority('alert:process') or hasAuthority('monitoring:config')")
    public Result<AiRiskReviewPoolItemVO> reviewScoreRecord(
            @Parameter(description = "评分记录ID", required = true) @PathVariable Long recordId,
            @Valid @RequestBody AiRiskReviewRequest request) {
        return Result.success(aiRiskScoringService.reviewScoreRecord(recordId, request));
    }

    @GetMapping("/review-pool/export")
    @Operation(summary = "导出AI评分待复核清单")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view') or hasAuthority('monitoring:view')")
    public ResponseEntity<byte[]> exportReviewPool(AiRiskReviewPoolQueryRequest request) {
        byte[] csvBytes = aiRiskScoringService.exportReviewPool(request);
        String filename = URLEncoder.encode("AI评分待复核清单.csv", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(csvBytes.length)
                .body(csvBytes);
    }

    @GetMapping("/customers/{customerId}")
    @Operation(summary = "客户AI风险评分")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('customer:view') or hasAuthority('monitoring:view')")
    public Result<AiRiskScoreVO> scoreCustomer(
            @Parameter(description = "客户ID", required = true) @PathVariable Long customerId) {
        log.debug("接收到客户AI风险评分请求，customerId={}", customerId);
        return Result.success(aiRiskScoringService.scoreCustomer(customerId));
    }

    @GetMapping("/customers/{customerId}/history")
    @Operation(summary = "客户AI风险评分历史")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('customer:view') or hasAuthority('monitoring:view')")
    public Result<List<AiRiskScoreRecordVO>> customerScoreHistory(
            @Parameter(description = "客户ID", required = true) @PathVariable Long customerId,
            @Parameter(description = "返回条数") @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(aiRiskScoringService.recentScores("CUSTOMER", customerId, limit));
    }

    @GetMapping("/transactions/{transactionId}")
    @Operation(summary = "交易AI风险评分")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('monitoring:view')")
    public Result<AiRiskScoreVO> scoreTransaction(
            @Parameter(description = "交易ID", required = true) @PathVariable Long transactionId) {
        log.debug("接收到交易AI风险评分请求，transactionId={}", transactionId);
        return Result.success(aiRiskScoringService.scoreTransaction(transactionId));
    }

    @GetMapping("/transactions/{transactionId}/history")
    @Operation(summary = "交易AI风险评分历史")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('monitoring:view')")
    public Result<List<AiRiskScoreRecordVO>> transactionScoreHistory(
            @Parameter(description = "交易ID", required = true) @PathVariable Long transactionId,
            @Parameter(description = "返回条数") @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(aiRiskScoringService.recentScores("TRANSACTION", transactionId, limit));
    }

    @GetMapping("/alerts/{alertId}")
    @Operation(summary = "预警AI风险评分")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('alert:view') or hasAuthority('monitoring:view')")
    public Result<AiRiskScoreVO> scoreAlert(
            @Parameter(description = "预警ID", required = true) @PathVariable Long alertId) {
        log.debug("接收到预警AI风险评分请求，alertId={}", alertId);
        return Result.success(aiRiskScoringService.scoreAlert(alertId));
    }

    @GetMapping("/alerts/{alertId}/history")
    @Operation(summary = "预警AI风险评分历史")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('alert:view') or hasAuthority('monitoring:view')")
    public Result<List<AiRiskScoreRecordVO>> alertScoreHistory(
            @Parameter(description = "预警ID", required = true) @PathVariable Long alertId,
            @Parameter(description = "返回条数") @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(aiRiskScoringService.recentScores("ALERT", alertId, limit));
    }
}
