package com.insurance.aml.module.ai.service;

import com.insurance.aml.module.ai.model.dto.AiRiskModelStatusVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolItemVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolOverviewVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolQueryRequest;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewRequest;
import com.insurance.aml.module.ai.model.dto.AiRiskScoreRecordVO;
import com.insurance.aml.module.ai.model.dto.AiRiskScoreVO;
import com.insurance.aml.module.ai.model.dto.AiRiskTrainingResultVO;
import com.insurance.aml.module.ai.model.dto.ModelTrainingStatusVO;
import com.insurance.aml.common.result.PageResult;

import java.util.List;

/**
 * AI辅助反洗钱风险评分服务。
 */
public interface AiRiskScoringService {

    AiRiskScoreVO scoreCustomer(Long customerId);

    AiRiskScoreVO scoreTransaction(Long transactionId);

    AiRiskScoreVO scoreAlert(Long alertId);

    List<AiRiskScoreRecordVO> recentScores(String subjectType, Long subjectId, Integer limit);

    PageResult<AiRiskReviewPoolItemVO> pageReviewPool(AiRiskReviewPoolQueryRequest request);

    AiRiskReviewPoolOverviewVO reviewPoolOverview();

    AiRiskReviewPoolItemVO reviewScoreRecord(Long recordId, AiRiskReviewRequest request);

    byte[] exportReviewPool(AiRiskReviewPoolQueryRequest request);

    AiRiskModelStatusVO getModelStatus();

    AiRiskTrainingResultVO retrainModel();

    AiRiskTrainingResultVO trainingStatus();

    List<ModelTrainingStatusVO> listTrainableModels();

    ModelTrainingStatusVO retrainModelByKey(String modelKey);
}
