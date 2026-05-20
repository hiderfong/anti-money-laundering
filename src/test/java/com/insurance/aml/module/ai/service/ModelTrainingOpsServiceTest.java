package com.insurance.aml.module.ai.service;

import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.module.ai.model.dto.AiRiskTrainingResultVO;
import com.insurance.aml.module.ai.model.dto.AnomalyTrainingResultVO;
import com.insurance.aml.module.ai.model.dto.ModelTrainingStatusVO;
import com.insurance.aml.module.ai.service.support.AiRiskModelTrainingService;
import com.insurance.aml.module.ai.service.support.ModelTrainingOpsService;
import com.insurance.aml.module.monitoring.service.TransactionAnomalyDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("模型训练运维聚合服务测试")
class ModelTrainingOpsServiceTest {

    @Mock
    AiRiskModelTrainingService aiRiskTrainingService;
    @Mock
    TransactionAnomalyDetector anomalyDetector;

    @InjectMocks
    ModelTrainingOpsService service;

    @Test
    @DisplayName("listAll 返回固定顺序 [supervised, anomaly]")
    void listAll_fixedOrder() {
        when(aiRiskTrainingService.trainingStatus()).thenReturn(
                AiRiskTrainingResultVO.builder().status("NOT_TRAINED").modelReady(false)
                        .message("尚未训练").build());
        when(anomalyDetector.getLastTrainStatus()).thenReturn(null);
        when(anomalyDetector.isModelReady()).thenReturn(false);

        List<ModelTrainingStatusVO> all = service.listAll();

        assertEquals(2, all.size());
        assertEquals("supervised", all.get(0).getModelKey());
        assertEquals("anomaly", all.get(1).getModelKey());
    }

    @Test
    @DisplayName("retrain(supervised) 委派到 AiRiskModelTrainingService.retrain()")
    void retrain_supervised_delegates() {
        when(aiRiskTrainingService.retrain()).thenReturn(
                AiRiskTrainingResultVO.builder().status("TRAINED").modelReady(true)
                        .sampleCount(50).message("ok").build());

        ModelTrainingStatusVO result = service.retrain("supervised");

        assertEquals("supervised", result.getModelKey());
        assertEquals("TRAINED", result.getStatus());
        assertEquals(50, result.getSampleCount());
    }

    @Test
    @DisplayName("retrain(anomaly) 委派到 TransactionAnomalyDetector.retrain()")
    void retrain_anomaly_delegates() {
        when(anomalyDetector.retrain()).thenReturn(
                AnomalyTrainingResultVO.builder().status("TRAINED").modelReady(true)
                        .sampleCount(120).trainDurationMs(42).message("ok").build());

        ModelTrainingStatusVO result = service.retrain("anomaly");

        assertEquals("anomaly", result.getModelKey());
        assertEquals("TRAINED", result.getStatus());
        assertEquals(120, result.getSampleCount());
    }

    @Test
    @DisplayName("未知 modelKey 抛 BAD_REQUEST")
    void retrain_unknown_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.retrain("not-a-model"));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("anomaly 上次训练 FAILED 时 message 包含错误详情")
    void listAll_anomalyFailed_surfacesError() {
        when(aiRiskTrainingService.trainingStatus()).thenReturn(
                AiRiskTrainingResultVO.builder().status("NOT_TRAINED").modelReady(false).message("尚未训练").build());
        when(anomalyDetector.getLastTrainStatus()).thenReturn("FAILED");
        when(anomalyDetector.getLastTrainError()).thenReturn("RuntimeException: db down");
        when(anomalyDetector.isModelReady()).thenReturn(false);

        List<ModelTrainingStatusVO> all = service.listAll();

        ModelTrainingStatusVO anomaly = all.get(1);
        assertEquals("anomaly", anomaly.getModelKey());
        assertEquals("FAILED", anomaly.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(
                anomaly.getMessage() != null && anomaly.getMessage().contains("db down"),
                "anomaly message 应包含 lastTrainError 内容");
    }
}
