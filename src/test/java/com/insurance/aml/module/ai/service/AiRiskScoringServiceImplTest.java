package com.insurance.aml.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.module.ai.mapper.AiRiskScoreRecordMapper;
import com.insurance.aml.module.ai.service.impl.AiRiskScoringServiceImpl;
import com.insurance.aml.module.ai.service.support.AiRiskFactorEvaluator;
import com.insurance.aml.module.ai.service.support.AiRiskFeatureBuilder;
import com.insurance.aml.module.ai.service.support.AiRiskReviewService;
import com.insurance.aml.module.alert.mapper.AlertMapper;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.modelmgmt.mapper.AmlModelMapper;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI风险评分编排器单元测试。
 *
 * <p>聚焦评分入口的主体存在性守卫；特征装配、因子打分与复核池逻辑分别由
 * 协作组件及其各自单测/集成测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AI风险评分编排器测试")
class AiRiskScoringServiceImplTest {

    @Mock
    CustomerMapper customerMapper;
    @Mock
    TransactionMapper transactionMapper;
    @Mock
    AlertMapper alertMapper;
    @Mock
    JdbcTemplate jdbcTemplate;
    @Mock
    AmlModelMapper amlModelMapper;
    @Mock
    AiRiskScoreRecordMapper scoreRecordMapper;
    @Mock
    ObjectMapper objectMapper;
    @Mock
    AiRiskFeatureBuilder featureBuilder;
    @Mock
    AiRiskFactorEvaluator factorEvaluator;
    @Mock
    AiRiskReviewService reviewService;

    @InjectMocks
    AiRiskScoringServiceImpl service;

    @Test
    @DisplayName("客户不存在时评分抛出CUSTOMER_NOT_FOUND且不落库")
    void scoreCustomer_customerNotFound_throws() {
        when(customerMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.scoreCustomer(99L));

        assertEquals(ResultCode.CUSTOMER_NOT_FOUND.getCode(), ex.getCode());
        verify(scoreRecordMapper, never()).insert(any());
    }

    @Test
    @DisplayName("交易不存在时评分抛出NOT_FOUND")
    void scoreTransaction_transactionNotFound_throws() {
        when(transactionMapper.selectById(88L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.scoreTransaction(88L));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        verify(scoreRecordMapper, never()).insert(any());
    }

    @Test
    @DisplayName("预警不存在时评分抛出ALERT_NOT_FOUND")
    void scoreAlert_alertNotFound_throws() {
        when(alertMapper.selectById(77L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.scoreAlert(77L));

        assertEquals(ResultCode.ALERT_NOT_FOUND.getCode(), ex.getCode());
        verify(scoreRecordMapper, never()).insert(any());
    }
}
