package com.insurance.aml.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.module.ai.mapper.AiRiskScoreRecordMapper;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolItemVO;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewPoolQueryRequest;
import com.insurance.aml.module.ai.model.dto.AiRiskReviewRequest;
import com.insurance.aml.module.ai.model.dto.AiRiskScoreRecordVO;
import com.insurance.aml.module.ai.model.entity.AiRiskScoreRecord;
import com.insurance.aml.module.ai.service.impl.AiRiskScoringServiceImpl;
import com.insurance.aml.module.alert.mapper.AlertMapper;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.modelmgmt.mapper.AmlModelMapper;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.service.TransactionAnomalyDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI风险评分服务单元测试。
 *
 * <p>覆盖未走数据库聚合的纯逻辑分支：主体不存在的守卫、复核标签归一化与校验、
 * 主体类型归一化、待复核池分页边界。重计算路径由
 * {@code AiRiskScoringIntegrationTest} 端到端覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AI风险评分服务测试")
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
    TransactionAnomalyDetector transactionAnomalyDetector;
    @Mock
    AmlModelMapper amlModelMapper;
    @Mock
    AiRiskScoreRecordMapper scoreRecordMapper;
    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    AiRiskScoringServiceImpl service;

    /** 构造一条主体为客户、customerId 为空的记录，确保 toReviewPoolItem 不触达数据库。 */
    private AiRiskScoreRecord customerRecordWithoutDbLookup() {
        AiRiskScoreRecord record = new AiRiskScoreRecord();
        record.setId(1L);
        record.setScoreNo("AIRS20260519001");
        record.setSubjectType("CUSTOMER");
        record.setSubjectId(1001L);
        record.setSubjectName("测试客户");
        record.setCustomerId(null);
        record.setScore(50);
        record.setRiskLevel("MEDIUM");
        record.setConfidence(70);
        record.setModelCode("AI_AML_RISK_BASELINE_V1");
        record.setModelVersion("1.0.0");
        return record;
    }

    @Test
    @DisplayName("客户不存在时评分抛出CUSTOMER_NOT_FOUND")
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
    }

    @Test
    @DisplayName("预警不存在时评分抛出ALERT_NOT_FOUND")
    void scoreAlert_alertNotFound_throws() {
        when(alertMapper.selectById(77L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.scoreAlert(77L));

        assertEquals(ResultCode.ALERT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("复核记录不存在时抛出NOT_FOUND")
    void reviewScoreRecord_recordNotFound_throws() {
        when(scoreRecordMapper.selectById(5L)).thenReturn(null);
        AiRiskReviewRequest request = new AiRiskReviewRequest();
        request.setReviewLabel("TRUE_POSITIVE");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reviewScoreRecord(5L, request));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        verify(scoreRecordMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("非法复核标签抛出BAD_REQUEST且不落库")
    void reviewScoreRecord_invalidLabel_throwsBadRequest() {
        when(scoreRecordMapper.selectById(1L)).thenReturn(customerRecordWithoutDbLookup());
        AiRiskReviewRequest request = new AiRiskReviewRequest();
        request.setReviewLabel("MAYBE");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reviewScoreRecord(1L, request));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(scoreRecordMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("合法复核标签被归一化为大写并写回，复核人取请求值")
    void reviewScoreRecord_validLabel_normalizesAndPersists() {
        AiRiskScoreRecord record = customerRecordWithoutDbLookup();
        when(scoreRecordMapper.selectById(1L)).thenReturn(record);
        AiRiskReviewRequest request = new AiRiskReviewRequest();
        request.setReviewLabel("true_positive");
        request.setReviewComment("  人工确认  ");
        request.setReviewer("qa-user");

        AiRiskReviewPoolItemVO item = service.reviewScoreRecord(1L, request);

        assertEquals("TRUE_POSITIVE", record.getManualReviewLabel());
        assertEquals("人工确认", record.getManualReviewComment());
        assertEquals("qa-user", record.getReviewedBy());
        assertEquals("CUSTOMER", item.getSubjectType());
        assertEquals("MANUAL_REVIEWED", item.getReviewStatus());
        verify(scoreRecordMapper, times(1)).updateById(record);
    }

    @Test
    @DisplayName("近期评分查询的主体类型非法时抛出BAD_REQUEST")
    void recentScores_invalidSubjectType_throwsBadRequest() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recentScores("UNKNOWN", 1L, 5));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("近期评分查询归一化主体类型并映射记录")
    void recentScores_normalizesTypeAndMapsRecords() {
        when(scoreRecordMapper.selectList(any())).thenReturn(List.of(customerRecordWithoutDbLookup()));

        List<AiRiskScoreRecordVO> result = service.recentScores("customer", 1001L, null);

        assertEquals(1, result.size());
        assertEquals("AIRS20260519001", result.get(0).getScoreNo());
        assertEquals("CUSTOMER", result.get(0).getSubjectType());
    }

    @Test
    @DisplayName("待复核池按页大小切片并计算总页数")
    void pageReviewPool_paginatesFilteredRecords() {
        when(scoreRecordMapper.selectList(any()))
                .thenReturn(List.of(customerRecordWithoutDbLookup(),
                        customerRecordWithoutDbLookup(),
                        customerRecordWithoutDbLookup()));
        AiRiskReviewPoolQueryRequest request = new AiRiskReviewPoolQueryRequest();
        request.setPage(1);
        request.setSize(2);

        PageResult<AiRiskReviewPoolItemVO> page = service.pageReviewPool(request);

        assertEquals(3, page.getTotal());
        assertEquals(2, page.getList().size());
        assertEquals(2, page.getTotalPages());
        assertEquals(1, page.getPage());
    }

    @Test
    @DisplayName("待复核池请求末页时返回剩余记录")
    void pageReviewPool_lastPageReturnsRemainder() {
        when(scoreRecordMapper.selectList(any()))
                .thenReturn(List.of(customerRecordWithoutDbLookup(),
                        customerRecordWithoutDbLookup(),
                        customerRecordWithoutDbLookup()));
        AiRiskReviewPoolQueryRequest request = new AiRiskReviewPoolQueryRequest();
        request.setPage(2);
        request.setSize(2);

        PageResult<AiRiskReviewPoolItemVO> page = service.pageReviewPool(request);

        assertEquals(3, page.getTotal());
        assertEquals(1, page.getList().size());
        assertEquals(2, page.getPage());
    }
}
