package com.insurance.aml.module.prevention.service;

import com.insurance.aml.common.enums.AlertStatus;
import com.insurance.aml.common.enums.RiskLevel;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.alert.mapper.AlertMapper;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.alert.service.AlertService;
import com.insurance.aml.module.casemgmt.model.dto.CaseCreateRequest;
import com.insurance.aml.module.casemgmt.model.entity.Case;
import com.insurance.aml.module.casemgmt.service.CaseService;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.prevention.mapper.FreezeSeizureDeductionMapper;
import com.insurance.aml.module.prevention.mapper.RetrospectiveScreeningJobMapper;
import com.insurance.aml.module.prevention.mapper.SpecialMeasureMapper;
import com.insurance.aml.module.prevention.mapper.WatchlistUpdateJobMapper;
import com.insurance.aml.module.prevention.model.dto.FreezeSeizureDeductionRequest;
import com.insurance.aml.module.prevention.model.dto.RetrospectiveScreeningJobRequest;
import com.insurance.aml.module.prevention.model.dto.SpecialMeasureRequest;
import com.insurance.aml.module.prevention.model.dto.WatchlistSyncRequest;
import com.insurance.aml.module.prevention.model.entity.FreezeSeizureDeduction;
import com.insurance.aml.module.prevention.model.entity.RetrospectiveScreeningJob;
import com.insurance.aml.module.prevention.model.entity.SpecialMeasure;
import com.insurance.aml.module.prevention.model.entity.WatchlistUpdateJob;
import com.insurance.aml.module.prevention.service.impl.SpecialPreventionServiceImpl;
import com.insurance.aml.module.screening.mapper.ScreeningResultMapper;
import com.insurance.aml.module.screening.mapper.WatchlistMapper;
import com.insurance.aml.module.screening.mapper.WatchlistSourceMapper;
import com.insurance.aml.module.screening.model.entity.ScreeningResult;
import com.insurance.aml.module.screening.model.entity.WatchlistSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("特别预防措施服务测试")
class SpecialPreventionServiceImplTest {

    @Mock private WatchlistUpdateJobMapper updateJobMapper;
    @Mock private RetrospectiveScreeningJobMapper retrospectiveJobMapper;
    @Mock private SpecialMeasureMapper specialMeasureMapper;
    @Mock private FreezeSeizureDeductionMapper freezeMapper;
    @Mock private WatchlistSourceMapper watchlistSourceMapper;
    @Mock private WatchlistMapper watchlistMapper;
    @Mock private ScreeningResultMapper screeningResultMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private AlertService alertService;
    @Mock private AlertMapper alertMapper;
    @Mock private CaseService caseService;
    @Mock private IdGenerator idGenerator;

    @InjectMocks private SpecialPreventionServiceImpl service;

    private Customer customer() {
        Customer c = new Customer();
        c.setId(2L);
        c.setName("张三");
        return c;
    }

    private ScreeningResult screeningResult(BigDecimal matchScore) {
        ScreeningResult r = new ScreeningResult();
        r.setId(1L);
        r.setCustomerId(2L);
        r.setCustomerName("张三");
        r.setWatchlistName("制裁名单");
        r.setMatchScore(matchScore);
        return r;
    }

    // ---------- createWatchlistUpdateJob ----------

    @Test
    @DisplayName("名单更新：名单源不存在抛BusinessException")
    void createWatchlistUpdateJob_sourceNotFound_throws() {
        WatchlistSyncRequest req = new WatchlistSyncRequest();
        req.setSourceId(99L);
        req.setUpdateMode("MANUAL");
        when(watchlistSourceMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createWatchlistUpdateJob(req));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("名单更新：sourceId为空使用全部名单源且无源副作用")
    void createWatchlistUpdateJob_nullSource_usesAllSources() {
        WatchlistSyncRequest req = new WatchlistSyncRequest();
        req.setUpdateMode("MANUAL");
        when(watchlistMapper.selectCount(any())).thenReturn(5L);
        when(idGenerator.generate("WUJ")).thenReturn("WUJ1");
        service.createWatchlistUpdateJob(req);
        ArgumentCaptor<WatchlistUpdateJob> captor = ArgumentCaptor.forClass(WatchlistUpdateJob.class);
        verify(updateJobMapper).insert(captor.capture());
        assertEquals("全部名单源", captor.getValue().getSourceName());
        assertEquals(5, captor.getValue().getTotalEntries());
        assertEquals("SUCCESS", captor.getValue().getStatus());
        verify(watchlistSourceMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("名单更新：有效名单源更新源统计")
    void createWatchlistUpdateJob_withSource_updatesSource() {
        WatchlistSyncRequest req = new WatchlistSyncRequest();
        req.setSourceId(1L);
        req.setUpdateMode("MANUAL");
        WatchlistSource source = new WatchlistSource();
        source.setSourceName("制裁名单");
        when(watchlistSourceMapper.selectById(1L)).thenReturn(source);
        when(watchlistMapper.selectCount(any())).thenReturn(3L);
        when(idGenerator.generate("WUJ")).thenReturn("WUJ2");
        service.createWatchlistUpdateJob(req);
        ArgumentCaptor<WatchlistUpdateJob> jobCaptor = ArgumentCaptor.forClass(WatchlistUpdateJob.class);
        verify(updateJobMapper).insert(jobCaptor.capture());
        assertEquals("制裁名单", jobCaptor.getValue().getSourceName());
        assertEquals(3, jobCaptor.getValue().getTotalEntries());
        ArgumentCaptor<WatchlistSource> srcCaptor = ArgumentCaptor.forClass(WatchlistSource.class);
        verify(watchlistSourceMapper).updateById(srcCaptor.capture());
        assertEquals(3, srcCaptor.getValue().getTotalEntries());
        assertNotNull(srcCaptor.getValue().getLastUpdateTime());
    }

    // ---------- createRetrospectiveJob / countCustomersByScope ----------

    @Test
    @DisplayName("回溯筛查：CUSTOMER_IDS按逗号计数")
    void createRetrospectiveJob_customerIds_countsByComma() {
        RetrospectiveScreeningJobRequest req = new RetrospectiveScreeningJobRequest();
        req.setJobName("J1");
        req.setScopeType("CUSTOMER_IDS");
        req.setCustomerIds("1,2,3");
        when(screeningResultMapper.selectCount(any())).thenReturn(0L);
        when(idGenerator.generate("RSJ")).thenReturn("RSJ1");
        service.createRetrospectiveJob(req);
        ArgumentCaptor<RetrospectiveScreeningJob> captor = ArgumentCaptor.forClass(RetrospectiveScreeningJob.class);
        verify(retrospectiveJobMapper).insert(captor.capture());
        assertEquals(3, captor.getValue().getTotalCustomers());
        assertEquals(3, captor.getValue().getProcessedCustomers());
    }

    @Test
    @DisplayName("回溯筛查：HIGH_RISK经客户Mapper计数")
    void createRetrospectiveJob_highRisk_countsViaMapper() {
        RetrospectiveScreeningJobRequest req = new RetrospectiveScreeningJobRequest();
        req.setJobName("J2");
        req.setScopeType("HIGH_RISK");
        when(customerMapper.selectCount(any())).thenReturn(7L);
        when(screeningResultMapper.selectCount(any())).thenReturn(2L);
        when(idGenerator.generate("RSJ")).thenReturn("RSJ2");
        service.createRetrospectiveJob(req);
        ArgumentCaptor<RetrospectiveScreeningJob> captor = ArgumentCaptor.forClass(RetrospectiveScreeningJob.class);
        verify(retrospectiveJobMapper).insert(captor.capture());
        assertEquals(7, captor.getValue().getTotalCustomers());
        assertEquals(2, captor.getValue().getTotalHits());
    }

    // ---------- createSpecialMeasure / updateSpecialMeasureStatus ----------

    @Test
    @DisplayName("特别措施：客户不存在抛BusinessException")
    void createSpecialMeasure_customerNotFound_throws() {
        SpecialMeasureRequest req = new SpecialMeasureRequest();
        req.setCustomerId(99L);
        when(customerMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createSpecialMeasure(req));
        assertEquals(ResultCode.CUSTOMER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("特别措施：controlLevel缺省为MEDIUM")
    void createSpecialMeasure_defaultControlLevel() {
        SpecialMeasureRequest req = new SpecialMeasureRequest();
        req.setCustomerId(2L);
        when(customerMapper.selectById(2L)).thenReturn(customer());
        when(idGenerator.generate("SPM")).thenReturn("SPM1");
        service.createSpecialMeasure(req);
        ArgumentCaptor<SpecialMeasure> captor = ArgumentCaptor.forClass(SpecialMeasure.class);
        verify(specialMeasureMapper).insert(captor.capture());
        assertEquals("MEDIUM", captor.getValue().getControlLevel());
    }

    @Test
    @DisplayName("特别措施：状态更新目标不存在抛异常")
    void updateSpecialMeasureStatus_notFound_throws() {
        when(specialMeasureMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateSpecialMeasureStatus(1L, "CLOSED", "reason"));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("特别措施：状态成功更新并记录关闭原因")
    void updateSpecialMeasureStatus_success_updatesStatusAndReason() {
        SpecialMeasure existing = new SpecialMeasure();
        existing.setId(1L);
        existing.setStatus("ACTIVE");
        when(specialMeasureMapper.selectById(1L)).thenReturn(existing);
        service.updateSpecialMeasureStatus(1L, "CLOSED", "已到期");
        ArgumentCaptor<SpecialMeasure> captor = ArgumentCaptor.forClass(SpecialMeasure.class);
        verify(specialMeasureMapper).updateById(captor.capture());
        assertEquals("CLOSED", captor.getValue().getStatus());
        assertEquals("已到期", captor.getValue().getClosedReason());
    }

    // ---------- createFreezeRecord / updateFreezeRecordStatus ----------

    @Test
    @DisplayName("查冻扣：客户不存在抛BusinessException")
    void createFreezeRecord_customerNotFound_throws() {
        FreezeSeizureDeductionRequest req = new FreezeSeizureDeductionRequest();
        req.setCustomerId(99L);
        when(customerMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createFreezeRecord(req));
        assertEquals(ResultCode.CUSTOMER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("查冻扣：currency缺省为CNY")
    void createFreezeRecord_defaultCurrency() {
        FreezeSeizureDeductionRequest req = new FreezeSeizureDeductionRequest();
        req.setCustomerId(2L);
        when(customerMapper.selectById(2L)).thenReturn(customer());
        when(idGenerator.generate("FSD")).thenReturn("FSD1");
        service.createFreezeRecord(req);
        ArgumentCaptor<FreezeSeizureDeduction> captor = ArgumentCaptor.forClass(FreezeSeizureDeduction.class);
        verify(freezeMapper).insert(captor.capture());
        assertEquals("CNY", captor.getValue().getCurrency());
    }

    @Test
    @DisplayName("查冻扣：状态更新目标不存在抛异常")
    void updateFreezeRecordStatus_notFound_throws() {
        when(freezeMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateFreezeRecordStatus(1L, "RELEASED", "remark"));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("查冻扣：状态成功更新并写入备注")
    void updateFreezeRecordStatus_success_updatesStatusAndRemark() {
        FreezeSeizureDeduction existing = new FreezeSeizureDeduction();
        existing.setId(1L);
        existing.setStatus("ACTIVE");
        existing.setRemark("旧备注");
        when(freezeMapper.selectById(1L)).thenReturn(existing);
        service.updateFreezeRecordStatus(1L, "RELEASED", "已解冻");
        ArgumentCaptor<FreezeSeizureDeduction> captor = ArgumentCaptor.forClass(FreezeSeizureDeduction.class);
        verify(freezeMapper).updateById(captor.capture());
        assertEquals("RELEASED", captor.getValue().getStatus());
        assertEquals("已解冻", captor.getValue().getRemark());
    }

    @Test
    @DisplayName("查冻扣：备注为空白时保留原备注")
    void updateFreezeRecordStatus_blankRemark_keepsExistingRemark() {
        FreezeSeizureDeduction existing = new FreezeSeizureDeduction();
        existing.setId(1L);
        existing.setStatus("ACTIVE");
        existing.setRemark("原始备注");
        when(freezeMapper.selectById(1L)).thenReturn(existing);
        service.updateFreezeRecordStatus(1L, "RELEASED", "   ");
        ArgumentCaptor<FreezeSeizureDeduction> captor = ArgumentCaptor.forClass(FreezeSeizureDeduction.class);
        verify(freezeMapper).updateById(captor.capture());
        assertEquals("RELEASED", captor.getValue().getStatus());
        assertEquals("原始备注", captor.getValue().getRemark());
    }

    // ---------- escalateScreeningResultToAlert ----------

    @Test
    @DisplayName("升级预警：筛查结果不存在抛BusinessException")
    void escalate_resultNotFound_throws() {
        when(screeningResultMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.escalateScreeningResultToAlert(1L, "reason"));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("升级预警：成功置ESCALATED并创建预警（分<95风险HIGH）")
    void escalate_success_setsEscalatedHighRisk() {
        when(screeningResultMapper.selectById(1L)).thenReturn(screeningResult(new BigDecimal("90")));
        Alert created = new Alert();
        created.setId(500L);
        when(alertService.createAlert(any(Alert.class), anyList())).thenReturn(created);
        Alert result;
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUsername).thenReturn("compliance");
            result = service.escalateScreeningResultToAlert(1L, "reason");
        }
        assertSame(created, result);
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertService).createAlert(alertCaptor.capture(), anyList());
        assertEquals(RiskLevel.HIGH.getCode(), alertCaptor.getValue().getRiskLevel());
        assertEquals("SCREENING:1", alertCaptor.getValue().getDeduplicateKey());
        ArgumentCaptor<ScreeningResult> resCaptor = ArgumentCaptor.forClass(ScreeningResult.class);
        verify(screeningResultMapper).updateById(resCaptor.capture());
        assertEquals("ESCALATED", resCaptor.getValue().getReviewStatus());
        assertEquals("compliance", resCaptor.getValue().getReviewedBy());
    }

    @Test
    @DisplayName("升级预警：matchScore=95边界判定CRITICAL")
    void escalate_boundaryScore95_isCritical() {
        when(screeningResultMapper.selectById(1L)).thenReturn(screeningResult(new BigDecimal("95")));
        Alert created = new Alert();
        created.setId(500L);
        when(alertService.createAlert(any(Alert.class), anyList())).thenReturn(created);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUsername).thenReturn("compliance");
            service.escalateScreeningResultToAlert(1L, "reason");
        }
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertService).createAlert(alertCaptor.capture(), anyList());
        assertEquals(RiskLevel.CRITICAL.getCode(), alertCaptor.getValue().getRiskLevel());
    }

    @Test
    @DisplayName("升级预警：matchScore为null时风险HIGH且评分默认90")
    void escalate_nullScore_isHighDefault90() {
        when(screeningResultMapper.selectById(1L)).thenReturn(screeningResult(null));
        Alert created = new Alert();
        created.setId(500L);
        when(alertService.createAlert(any(Alert.class), anyList())).thenReturn(created);
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUsername).thenReturn("compliance");
            service.escalateScreeningResultToAlert(1L, "reason");
        }
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertService).createAlert(alertCaptor.capture(), anyList());
        assertEquals(RiskLevel.HIGH.getCode(), alertCaptor.getValue().getRiskLevel());
        assertEquals(90, alertCaptor.getValue().getRiskScore());
    }

    // ---------- createCaseFromScreeningResult ----------

    @Test
    @DisplayName("筛查建案：高分(≥95)风险CRITICAL优先级5")
    void createCase_highScore_priority5() {
        when(screeningResultMapper.selectById(1L)).thenReturn(screeningResult(new BigDecimal("96")));
        Alert created = new Alert();
        created.setId(500L);
        when(alertService.createAlert(any(Alert.class), anyList())).thenReturn(created);
        when(caseService.createCase(any(CaseCreateRequest.class))).thenReturn(new Case());
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUsername).thenReturn("compliance");
            service.createCaseFromScreeningResult(1L, "reason");
        }
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertService).createAlert(alertCaptor.capture(), anyList());
        assertEquals(RiskLevel.CRITICAL.getCode(), alertCaptor.getValue().getRiskLevel());
        ArgumentCaptor<Alert> alertUpdateCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertMapper).updateById(alertUpdateCaptor.capture());
        assertEquals(AlertStatus.CONFIRMED.getCode(), alertUpdateCaptor.getValue().getStatus());
        assertEquals("CONFIRMED_SUSPICIOUS", alertUpdateCaptor.getValue().getProcessResult());
        ArgumentCaptor<CaseCreateRequest> caseCaptor = ArgumentCaptor.forClass(CaseCreateRequest.class);
        verify(caseService).createCase(caseCaptor.capture());
        assertEquals(5, caseCaptor.getValue().getPriority());
        assertEquals("WATCHLIST_HIT", caseCaptor.getValue().getCaseType());
        assertEquals(500L, caseCaptor.getValue().getAlertId());
    }

    @Test
    @DisplayName("筛查建案：低分(<95)风险HIGH优先级4")
    void createCase_lowScore_priority4() {
        when(screeningResultMapper.selectById(1L)).thenReturn(screeningResult(new BigDecimal("80")));
        Alert created = new Alert();
        created.setId(500L);
        when(alertService.createAlert(any(Alert.class), anyList())).thenReturn(created);
        when(caseService.createCase(any(CaseCreateRequest.class))).thenReturn(new Case());
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUsername).thenReturn("compliance");
            service.createCaseFromScreeningResult(1L, "reason");
        }
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertService).createAlert(alertCaptor.capture(), anyList());
        assertEquals(RiskLevel.HIGH.getCode(), alertCaptor.getValue().getRiskLevel());
        ArgumentCaptor<CaseCreateRequest> caseCaptor = ArgumentCaptor.forClass(CaseCreateRequest.class);
        verify(caseService).createCase(caseCaptor.capture());
        assertEquals(4, caseCaptor.getValue().getPriority());
        assertEquals(500L, caseCaptor.getValue().getAlertId());
    }

    @Test
    @DisplayName("筛查建案：matchScore=95边界优先级5")
    void createCase_boundaryScore95_priority5() {
        when(screeningResultMapper.selectById(1L)).thenReturn(screeningResult(new BigDecimal("95")));
        Alert created = new Alert();
        created.setId(500L);
        when(alertService.createAlert(any(Alert.class), anyList())).thenReturn(created);
        when(caseService.createCase(any(CaseCreateRequest.class))).thenReturn(new Case());
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUsername).thenReturn("compliance");
            service.createCaseFromScreeningResult(1L, "reason");
        }
        ArgumentCaptor<CaseCreateRequest> caseCaptor = ArgumentCaptor.forClass(CaseCreateRequest.class);
        verify(caseService).createCase(caseCaptor.capture());
        assertEquals(5, caseCaptor.getValue().getPriority());
    }
}
