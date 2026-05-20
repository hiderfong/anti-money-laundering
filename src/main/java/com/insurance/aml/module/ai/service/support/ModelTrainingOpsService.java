package com.insurance.aml.module.ai.service.support;

import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.module.ai.model.dto.AiRiskTrainingResultVO;
import com.insurance.aml.module.ai.model.dto.AnomalyTrainingResultVO;
import com.insurance.aml.module.ai.model.dto.ModelTrainingStatusVO;
import com.insurance.aml.module.monitoring.service.TransactionAnomalyDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 训练运维聚合服务：把监督模型与无监督异常检测的训练状态/触发统一对外。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelTrainingOpsService {

    public static final String MODEL_KEY_SUPERVISED = "supervised";
    public static final String MODEL_KEY_ANOMALY = "anomaly";

    private static final String SUPERVISED_NAME = "AI可解释风险评分基线模型";
    private static final String SUPERVISED_VERSION = "1.0.0";
    private static final String ANOMALY_NAME = "交易异常检测 (Isolation Forest)";
    private static final String ANOMALY_VERSION = "1.0.0";

    private final AiRiskModelTrainingService aiRiskTrainingService;
    private final TransactionAnomalyDetector anomalyDetector;

    public List<ModelTrainingStatusVO> listAll() {
        return List.of(supervisedStatus(), anomalyStatus());
    }

    public ModelTrainingStatusVO retrain(String modelKey) {
        if (MODEL_KEY_SUPERVISED.equals(modelKey)) {
            return toStatus(aiRiskTrainingService.retrain());
        }
        if (MODEL_KEY_ANOMALY.equals(modelKey)) {
            return toStatus(anomalyDetector.retrain());
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的模型: " + modelKey);
    }

    private ModelTrainingStatusVO supervisedStatus() {
        return toStatus(aiRiskTrainingService.trainingStatus());
    }

    private ModelTrainingStatusVO anomalyStatus() {
        String last = anomalyDetector.getLastTrainStatus();
        String error = anomalyDetector.getLastTrainError();
        boolean ready = anomalyDetector.isModelReady();
        String message;
        if ("FAILED".equals(last) && error != null) {
            message = "上次训练失败: " + error;
        } else if (last == null) {
            message = ready ? "模型就绪" : "模型尚未训练";
        } else {
            message = ready ? "模型就绪 (上次: " + last + ")" : "模型尚未训练 (上次: " + last + ")";
        }
        return ModelTrainingStatusVO.builder()
                .modelKey(MODEL_KEY_ANOMALY)
                .modelName(ANOMALY_NAME)
                .modelVersion(ANOMALY_VERSION)
                .status(last == null ? (ready ? "TRAINED" : "NOT_TRAINED") : last)
                .modelReady(ready)
                .sampleCount(anomalyDetector.getLastTrainSampleCount())
                .trainedAt(anomalyDetector.getLastTrainedAt())
                .message(message)
                .build();
    }

    private ModelTrainingStatusVO toStatus(AiRiskTrainingResultVO r) {
        return ModelTrainingStatusVO.builder()
                .modelKey(MODEL_KEY_SUPERVISED)
                .modelName(SUPERVISED_NAME)
                .modelVersion(SUPERVISED_VERSION)
                .status(r.getStatus())
                .modelReady(r.isModelReady())
                .sampleCount(r.getSampleCount())
                .trainedAt(r.getTrainedAt())
                .message(r.getMessage())
                .build();
    }

    private ModelTrainingStatusVO toStatus(AnomalyTrainingResultVO r) {
        return ModelTrainingStatusVO.builder()
                .modelKey(MODEL_KEY_ANOMALY)
                .modelName(ANOMALY_NAME)
                .modelVersion(ANOMALY_VERSION)
                .status(r.getStatus())
                .modelReady(r.isModelReady())
                .sampleCount(r.getSampleCount())
                .trainedAt(r.getTrainedAt())
                .message(r.getMessage())
                .build();
    }
}
