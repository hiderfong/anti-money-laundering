package com.insurance.aml.module.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.module.ai.mapper.AiRiskScoreRecordMapper;
import com.insurance.aml.module.ai.model.dto.AiRiskFactorVO;
import com.insurance.aml.module.ai.model.dto.AiRiskFeatureSummaryVO;
import com.insurance.aml.module.ai.model.dto.AiRiskModelStatusVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolItemVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolOverviewVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolQueryRequest;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewRequest;
import com.insurance.aml.module.ai.model.dto.AiRiskScoreRecordVO;
import com.insurance.aml.module.ai.model.dto.AiRiskScoreVO;
import com.insurance.aml.module.ai.model.dto.AiRiskTrainingResultVO;
import com.insurance.aml.module.ai.model.dto.ModelDriftStatusVO;
import com.insurance.aml.module.ai.model.dto.ModelTrainingStatusVO;
import com.insurance.aml.module.ai.model.entity.AiRiskScoreRecord;
import com.insurance.aml.module.ai.service.AiRiskScoringService;
import com.insurance.aml.module.ai.service.support.AiRiskFactorEvaluator;
import com.insurance.aml.module.ai.service.support.AiRiskFeatureBuilder;
import com.insurance.aml.module.ai.service.support.AiRiskFeatureVectorizer;
import com.insurance.aml.module.ai.service.support.AiRiskModelTrainingService;
import com.insurance.aml.module.ai.service.support.AiRiskReviewService;
import com.insurance.aml.module.ai.service.support.ModelDriftMonitorService;
import com.insurance.aml.module.ai.service.support.ModelTrainingOpsService;
import com.insurance.aml.module.ai.service.support.AiRiskSupervisedModel;
import com.insurance.aml.module.alert.mapper.AlertMapper;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.modelmgmt.mapper.AmlModelMapper;
import com.insurance.aml.module.modelmgmt.model.entity.AmlModel;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 可解释AI风险评分服务编排器。
 *
 * <p>仅负责评分链路编排：取主体 → 装配特征（{@link AiRiskFeatureBuilder}）→
 * 评估因子/置信度/证据（{@link AiRiskFactorEvaluator}）→ 落库；复核池与记录
 * 查询委托 {@link AiRiskReviewService}。规则细节已下沉到协作组件以便审阅与单测。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRiskScoringServiceImpl implements AiRiskScoringService {

    private static final String MODEL_CODE = "AI_AML_RISK_BASELINE_V1";
    private static final String MODEL_NAME = "AI可解释风险评分基线模型";
    private static final String MODEL_VERSION = "1.0.0";

    private final CustomerMapper customerMapper;
    private final TransactionMapper transactionMapper;
    private final AlertMapper alertMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AmlModelMapper amlModelMapper;
    private final AiRiskScoreRecordMapper scoreRecordMapper;
    private final ObjectMapper objectMapper;
    private final AiRiskFeatureBuilder featureBuilder;
    private final AiRiskFactorEvaluator factorEvaluator;
    private final AiRiskReviewService reviewService;
    private final AiRiskSupervisedModel supervisedModel;
    private final AiRiskFeatureVectorizer featureVectorizer;
    private final AiRiskModelTrainingService trainingService;
    private final ModelTrainingOpsService modelTrainingOpsService;
    private final ModelDriftMonitorService modelDriftMonitorService;

    @Override
    public AiRiskScoreVO scoreCustomer(Long customerId) {
        Customer customer = findCustomer(customerId);
        AiRiskFeatureSummaryVO features = featureBuilder.buildCustomerFeatures(customer);

        List<AiRiskFactorVO> factors = new ArrayList<>();
        factorEvaluator.addIdentityFactors(factors, customer);
        factorEvaluator.addKycFactor(factors, features);
        factorEvaluator.addTransactionBehaviorFactors(factors, features);
        factorEvaluator.addDispositionFactors(factors, features);
        factorEvaluator.addRelationshipFactors(factors, features);

        AmlModel model = resolveServingModel();
        AiRiskScoreVO result = buildResult("CUSTOMER", customer.getId(), customer.getName(), features, factors);
        applyModelContext(result, model);
        result.setConfidence(factorEvaluator.calculateCustomerConfidence(customer, features));
        factorEvaluator.addCustomerEvidenceAndRecommendations(result, customer, features);
        return persistScoreRecord(result, customer.getId(), null, null, model);
    }

    @Override
    public AiRiskScoreVO scoreTransaction(Long transactionId) {
        Transaction transaction = transactionMapper.selectById(transactionId);
        if (transaction == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "交易不存在，ID：" + transactionId);
        }

        Customer customer = findCustomer(transaction.getCustomerId());
        AiRiskFeatureSummaryVO features = featureBuilder.buildCustomerFeatures(customer);
        featureBuilder.enrichTransactionFeatures(features, transaction);

        List<AiRiskFactorVO> factors = new ArrayList<>();
        factorEvaluator.addTransactionAmountFactor(factors, transaction, features);
        factorEvaluator.addTransactionAttributeFactors(factors, transaction, features);
        factorEvaluator.addCustomerContextFactors(factors, customer);
        factorEvaluator.addRelatedAlertFactor(factors, features);

        AmlModel model = resolveServingModel();
        AiRiskScoreVO result = buildResult("TRANSACTION", transaction.getId(), transaction.getTransactionNo(), features, factors);
        applyModelContext(result, model);
        result.setConfidence(factorEvaluator.calculateTransactionConfidence(transaction, features));
        factorEvaluator.addTransactionEvidenceAndRecommendations(result, transaction, customer, features);
        return persistScoreRecord(result, transaction.getCustomerId(), transaction.getId(), null, model);
    }

    @Override
    public AiRiskScoreVO scoreAlert(Long alertId) {
        Alert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new BusinessException(ResultCode.ALERT_NOT_FOUND, "预警不存在，ID：" + alertId);
        }

        Customer customer = findCustomer(alert.getCustomerId());
        AiRiskFeatureSummaryVO features = featureBuilder.buildCustomerFeatures(customer);
        features.setRelatedAlertCount(featureBuilder.countRelatedTransactions(alert.getRelatedTransactionIds()));

        List<AiRiskFactorVO> factors = new ArrayList<>();
        factorEvaluator.addAlertNativeFactor(factors, alert);
        factorEvaluator.addCustomerContextFactors(factors, customer);
        factorEvaluator.addDispositionFactors(factors, features);
        factorEvaluator.addAlertStatusFactor(factors, alert);

        AmlModel model = resolveServingModel();
        AiRiskScoreVO result = buildResult("ALERT", alert.getId(), alert.getAlertNo(), features, factors);
        applyModelContext(result, model);
        result.setConfidence(factorEvaluator.calculateAlertConfidence(alert, features));
        factorEvaluator.addAlertEvidenceAndRecommendations(result, alert, customer, features);
        return persistScoreRecord(result, alert.getCustomerId(), null, alert.getId(), model);
    }

    @Override
    public List<AiRiskScoreRecordVO> recentScores(String subjectType, Long subjectId, Integer limit) {
        return reviewService.recentScores(subjectType, subjectId, limit);
    }

    @Override
    public PageResult<AiRiskReviewPoolItemVO> pageReviewPool(AiRiskReviewPoolQueryRequest request) {
        return reviewService.pageReviewPool(request);
    }

    @Override
    public AiRiskReviewPoolOverviewVO reviewPoolOverview() {
        return reviewService.reviewPoolOverview();
    }

    @Override
    public AiRiskReviewPoolItemVO reviewScoreRecord(Long recordId, AiRiskReviewRequest request) {
        return reviewService.reviewScoreRecord(recordId, request);
    }

    @Override
    public byte[] exportReviewPool(AiRiskReviewPoolQueryRequest request) {
        return reviewService.exportReviewPool(request);
    }

    @Override
    public AiRiskTrainingResultVO retrainModel() {
        return trainingService.retrain();
    }

    @Override
    public AiRiskTrainingResultVO trainingStatus() {
        return trainingService.trainingStatus();
    }

    @Override
    public List<ModelTrainingStatusVO> listTrainableModels() {
        return modelTrainingOpsService.listAll();
    }

    @Override
    public ModelTrainingStatusVO retrainModelByKey(String modelKey) {
        return modelTrainingOpsService.retrain(modelKey);
    }

    @Override
    public List<ModelDriftStatusVO> listModelDrift() {
        return modelDriftMonitorService.computeAll();
    }

    @Override
    public AiRiskModelStatusVO getModelStatus() {
        AmlModel model = resolveServingModel();
        AiRiskModelStatusVO status = new AiRiskModelStatusVO();
        applyStatusModelContext(status, model);
        Map<String, Object> stats = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS recordCount, MAX(scored_at) AS lastScoredAt
                FROM t_ai_risk_score_record
                WHERE model_code = ?
                """, MODEL_CODE);
        status.setScoringRecordCount(longValue(stats, "recordCount"));
        Object lastScoredAt = stats.get("lastScoredAt");
        if (lastScoredAt instanceof LocalDateTime time) {
            status.setLastScoredAt(time);
        } else if (lastScoredAt instanceof java.sql.Timestamp timestamp) {
            status.setLastScoredAt(timestamp.toLocalDateTime());
        }
        return status;
    }

    private Customer findCustomer(Long customerId) {
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException(ResultCode.CUSTOMER_NOT_FOUND, "客户不存在，ID：" + customerId);
        }
        return customer;
    }

    private AiRiskScoreVO buildResult(String subjectType, Long subjectId, String subjectName,
                                      AiRiskFeatureSummaryVO features, List<AiRiskFactorVO> factors) {
        AiRiskScoreVO result = new AiRiskScoreVO();
        result.setSubjectType(subjectType);
        result.setSubjectId(subjectId);
        result.setSubjectName(subjectName);
        result.setFeatureSummary(features);
        factors.sort(Comparator.comparingInt(AiRiskFactorVO::getContribution).reversed());
        result.setFactors(factors);
        int score = clamp(factors.stream().mapToInt(AiRiskFactorVO::getContribution).sum());
        result.setScore(score);
        result.setRiskLevel(toRiskLevel(score));
        result.setScoredAt(LocalDateTime.now());
        return result;
    }

    private AmlModel resolveServingModel() {
        return amlModelMapper.selectOne(new LambdaQueryWrapper<AmlModel>()
                .eq(AmlModel::getModelCode, MODEL_CODE)
                .last("LIMIT 1"));
    }

    private void applyModelContext(AiRiskScoreVO result, AmlModel model) {
        if (model == null) {
            result.setModelCode(MODEL_CODE);
            result.setModelName(MODEL_NAME);
            result.setModelVersion(MODEL_VERSION);
            return;
        }
        result.setModelId(model.getId());
        result.setModelCode(model.getModelCode());
        result.setModelName(model.getModelName());
        result.setModelVersion(StringUtils.hasText(model.getVersion()) ? model.getVersion() : MODEL_VERSION);
    }

    private void applyStatusModelContext(AiRiskModelStatusVO status, AmlModel model) {
        if (model == null) {
            status.setModelCode(MODEL_CODE);
            status.setModelName(MODEL_NAME);
            status.setModelVersion(MODEL_VERSION);
            return;
        }
        status.setModelId(model.getId());
        status.setModelCode(model.getModelCode());
        status.setModelName(model.getModelName());
        status.setModelVersion(StringUtils.hasText(model.getVersion()) ? model.getVersion() : MODEL_VERSION);
        status.setLifecycleStatus(model.getLifecycleStatus());
        status.setMonitorStatus(model.getMonitorStatus());
        status.setStatus("MONITORING".equals(model.getLifecycleStatus()) || "DEPLOYED".equals(model.getLifecycleStatus())
                ? "SERVING"
                : model.getLifecycleStatus());
        status.setDescription(StringUtils.hasText(model.getDescription()) ? model.getDescription() : status.getDescription());
    }

    private AiRiskScoreVO persistScoreRecord(AiRiskScoreVO result, Long customerId, Long transactionId, Long alertId, AmlModel model) {
        AiRiskScoreRecord record = new AiRiskScoreRecord();
        record.setScoreNo(generateScoreNo());
        record.setSubjectType(result.getSubjectType());
        record.setSubjectId(result.getSubjectId());
        record.setSubjectName(result.getSubjectName());
        record.setCustomerId(customerId);
        record.setTransactionId(transactionId);
        record.setAlertId(alertId);
        record.setModelId(model == null ? null : model.getId());
        record.setModelCode(result.getModelCode());
        record.setModelName(result.getModelName());
        record.setModelVersion(result.getModelVersion());
        record.setScore(result.getScore());
        record.setRiskLevel(result.getRiskLevel());
        record.setConfidence(result.getConfidence());
        record.setFactorSummary(buildFactorSummary(result));
        record.setFeatureSnapshotJson(toJson(result.getFeatureSummary()));
        record.setFactorSnapshotJson(toJson(result.getFactors()));
        record.setEvidenceSnapshotJson(toJson(result.getEvidence()));
        record.setRecommendationJson(toJson(result.getRecommendations()));
        record.setScoredAt(result.getScoredAt());
        applyShadowModelScore(record, result.getFeatureSummary());
        scoreRecordMapper.insert(record);

        result.setRecordId(record.getId());
        result.setScoreNo(record.getScoreNo());
        return result;
    }

    private String generateScoreNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "AIRISK" + timestamp + suffix;
    }

    /**
     * 影子写入监督模型概率分。失败仅告警，绝不影响规则评分与落库。
     */
    private void applyShadowModelScore(AiRiskScoreRecord record, AiRiskFeatureSummaryVO features) {
        try {
            if (features == null || !supervisedModel.isReady()) {
                return;
            }
            supervisedModel.predictProbability(featureVectorizer.toVector(features))
                    .ifPresent(prob -> {
                        record.setModelProbability(
                                java.math.BigDecimal.valueOf(prob).setScale(4, java.math.RoundingMode.HALF_UP));
                        record.setModelLabelPredicted(prob >= 0.5 ? "SUSPICIOUS" : "NORMAL");
                    });
        } catch (Exception e) {
            log.warn("AI监督模型影子评分失败，不影响规则评分: {}", e.getMessage());
        }
    }

    private String buildFactorSummary(AiRiskScoreVO result) {
        if (result.getFactors() == null || result.getFactors().isEmpty()) {
            return "未识别明显高贡献风险因子";
        }
        return result.getFactors().stream()
                .limit(5)
                .map(factor -> factor.getFactorName() + "(+" + factor.getContribution() + ")")
                .reduce((left, right) -> left + "；" + right)
                .orElse("未识别明显高贡献风险因子");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("AI风险评分快照序列化失败", ex);
            return "{}";
        }
    }

    private String toRiskLevel(int score) {
        if (score >= 85) return "CRITICAL";
        if (score >= 65) return "HIGH";
        if (score >= 35) return "MEDIUM";
        return "LOW";
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private long longValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
