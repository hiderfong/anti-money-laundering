package com.insurance.aml.module.ai.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI风险评分记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ai_risk_score_record")
public class AiRiskScoreRecord extends BaseEntity {

    private String scoreNo;
    private String subjectType;
    private Long subjectId;
    private String subjectName;
    private Long customerId;
    private Long transactionId;
    private Long alertId;
    private Long modelId;
    private String modelCode;
    private String modelName;
    private String modelVersion;
    private Integer score;
    private String riskLevel;
    private Integer confidence;
    private String factorSummary;
    private String featureSnapshotJson;
    private String factorSnapshotJson;
    private String evidenceSnapshotJson;
    private String recommendationJson;
    private LocalDateTime scoredAt;
    private String manualReviewLabel;
    private String manualReviewComment;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
}
