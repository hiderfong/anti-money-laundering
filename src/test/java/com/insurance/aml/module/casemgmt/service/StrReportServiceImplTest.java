package com.insurance.aml.module.casemgmt.service;

import com.insurance.aml.common.enums.CaseStatus;
import com.insurance.aml.common.enums.ReportStatus;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.module.casemgmt.mapper.CaseMapper;
import com.insurance.aml.module.casemgmt.mapper.StrReportMapper;
import com.insurance.aml.module.casemgmt.model.dto.StrReportCreateRequest;
import com.insurance.aml.module.casemgmt.model.dto.StrReportReviewRequest;
import com.insurance.aml.module.casemgmt.model.entity.Case;
import com.insurance.aml.module.casemgmt.model.entity.StrReport;
import com.insurance.aml.module.casemgmt.service.CaseService;
import com.insurance.aml.module.casemgmt.service.impl.StrReportServiceImpl;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.reporting.mapper.ReportSubmitLogMapper;
import com.insurance.aml.module.reporting.model.entity.ReportSubmitLog;
import com.insurance.aml.module.reporting.service.XmlGeneratorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STR（可疑交易报告）服务单元测试。
 * 覆盖 create→submitForReview→review→submitToRegulator 状态机的守卫与转换。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("STR可疑交易报告服务测试")
class StrReportServiceImplTest {

    @Mock StrReportMapper strReportMapper;
    @Mock CaseMapper caseMapper;
    @Mock CustomerMapper customerMapper;
    @Mock IdGenerator idGenerator;
    @Mock XmlGeneratorService xmlGeneratorService;
    @Mock ReportSubmitLogMapper reportSubmitLogMapper;
    @Mock CaseService caseService;

    @InjectMocks StrReportServiceImpl service;

    private StrReport reportWithStatus(String status) {
        StrReport r = new StrReport();
        r.setId(100L);
        r.setReportNo("STR-100");
        r.setCaseId(7L);
        r.setCustomerId(9L);
        r.setReportStatus(status);
        return r;
    }

    // ---- createReport ----

    @Test
    @DisplayName("创建报告-案件不存在-抛异常且不落库")
    void createReport_caseNotFound_throws() {
        when(caseMapper.selectById(7L)).thenReturn(null);
        StrReportCreateRequest req = new StrReportCreateRequest();
        req.setCaseId(7L);

        assertThrows(BusinessException.class, () -> service.createReport(req));
        verify(strReportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("创建报告-同案件已有报告-抛异常")
    void createReport_duplicateForCase_throws() {
        Case c = new Case();
        c.setId(7L);
        c.setCustomerId(9L);
        when(caseMapper.selectById(7L)).thenReturn(c);
        when(strReportMapper.selectCount(any())).thenReturn(1L);
        StrReportCreateRequest req = new StrReportCreateRequest();
        req.setCaseId(7L);

        assertThrows(BusinessException.class, () -> service.createReport(req));
        verify(strReportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("创建报告-正常-DRAFT状态且customerId取自案件")
    void createReport_happyPath_draft() {
        Case c = new Case();
        c.setId(7L);
        c.setCustomerId(9L);
        when(caseMapper.selectById(7L)).thenReturn(c);
        when(strReportMapper.selectCount(any())).thenReturn(0L);
        when(idGenerator.generateReportNo()).thenReturn("STR-NEW");
        StrReportCreateRequest req = new StrReportCreateRequest();
        req.setCaseId(7L);
        req.setReportType("SUSPICIOUS");

        StrReport result = service.createReport(req);

        assertEquals(ReportStatus.DRAFT.getCode(), result.getReportStatus());
        assertEquals(9L, result.getCustomerId());
        assertEquals("STR-NEW", result.getReportNo());
        verify(strReportMapper, times(1)).insert(any());
    }

    // ---- submitForReview ----

    @Test
    @DisplayName("提交审核-报告不存在-抛异常")
    void submitForReview_notFound_throws() {
        when(strReportMapper.selectById(100L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.submitForReview(100L));
    }

    @Test
    @DisplayName("提交审核-非DRAFT状态-抛异常")
    void submitForReview_nonDraft_throws() {
        when(strReportMapper.selectById(100L)).thenReturn(reportWithStatus(ReportStatus.APPROVED.getCode()));
        assertThrows(BusinessException.class, () -> service.submitForReview(100L));
        verify(strReportMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("提交审核-DRAFT-转PENDING_REVIEW")
    void submitForReview_draft_transitionsToPendingReview() {
        StrReport r = reportWithStatus(ReportStatus.DRAFT.getCode());
        when(strReportMapper.selectById(100L)).thenReturn(r);

        service.submitForReview(100L);

        assertEquals(ReportStatus.PENDING_REVIEW.getCode(), r.getReportStatus());
        verify(strReportMapper, times(1)).updateById(r);
    }

    // ---- reviewReport ----

    @Test
    @DisplayName("审核-报告不存在-抛异常")
    void reviewReport_notFound_throws() {
        when(strReportMapper.selectById(100L)).thenReturn(null);
        StrReportReviewRequest req = new StrReportReviewRequest();
        req.setReportId(100L);
        req.setApproved(Boolean.TRUE);
        assertThrows(BusinessException.class, () -> service.reviewReport(req));
    }

    @Test
    @DisplayName("审核-非PENDING_REVIEW状态-抛异常")
    void reviewReport_nonPending_throws() {
        when(strReportMapper.selectById(100L)).thenReturn(reportWithStatus(ReportStatus.DRAFT.getCode()));
        StrReportReviewRequest req = new StrReportReviewRequest();
        req.setReportId(100L);
        req.setApproved(Boolean.TRUE);
        assertThrows(BusinessException.class, () -> service.reviewReport(req));
        verify(caseService, never()).changeCaseStatus(any(), any(), any());
    }

    @Test
    @DisplayName("审核-批准-转APPROVED并推进案件到PENDING_APPROVAL")
    void reviewReport_approved_advancesCase() {
        StrReport r = reportWithStatus(ReportStatus.PENDING_REVIEW.getCode());
        when(strReportMapper.selectById(100L)).thenReturn(r);
        StrReportReviewRequest req = new StrReportReviewRequest();
        req.setReportId(100L);
        req.setApproved(Boolean.TRUE);
        req.setOpinion("通过");

        service.reviewReport(req);

        assertEquals(ReportStatus.APPROVED.getCode(), r.getReportStatus());
        verify(caseService, times(1)).changeCaseStatus(eq(7L),
                eq(CaseStatus.PENDING_APPROVAL.getCode()), anyString());
    }

    @Test
    @DisplayName("审核-拒绝-转REJECTED且不动案件")
    void reviewReport_rejected_doesNotTouchCase() {
        StrReport r = reportWithStatus(ReportStatus.PENDING_REVIEW.getCode());
        when(strReportMapper.selectById(100L)).thenReturn(r);
        StrReportReviewRequest req = new StrReportReviewRequest();
        req.setReportId(100L);
        req.setApproved(Boolean.FALSE);
        req.setOpinion("驳回");

        service.reviewReport(req);

        assertEquals(ReportStatus.REJECTED.getCode(), r.getReportStatus());
        verify(caseService, never()).changeCaseStatus(any(), any(), any());
    }

    // ---- submitToRegulator ----

    @Test
    @DisplayName("提交监管-报告不存在-抛异常")
    void submitToRegulator_notFound_throws() {
        when(strReportMapper.selectById(100L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.submitToRegulator(100L));
    }

    @Test
    @DisplayName("提交监管-非APPROVED状态-拒绝(合规关键)")
    void submitToRegulator_nonApproved_throws() {
        when(strReportMapper.selectById(100L)).thenReturn(reportWithStatus(ReportStatus.PENDING_REVIEW.getCode()));
        assertThrows(BusinessException.class, () -> service.submitToRegulator(100L));
        verify(reportSubmitLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("提交监管-APPROVED-转SUBMITTED并生成XML写成功日志")
    void submitToRegulator_approved_submitsAndLogs() {
        StrReport r = reportWithStatus(ReportStatus.APPROVED.getCode());
        when(strReportMapper.selectById(100L)).thenReturn(r);
        when(xmlGeneratorService.generateSuspiciousTxnXml(r)).thenReturn("<str/>");

        service.submitToRegulator(100L);

        assertEquals(ReportStatus.SUBMITTED.getCode(), r.getReportStatus());
        verify(xmlGeneratorService, times(1)).generateSuspiciousTxnXml(r);
        ArgumentCaptor<ReportSubmitLog> captor = ArgumentCaptor.forClass(ReportSubmitLog.class);
        verify(reportSubmitLogMapper, times(1)).insert(captor.capture());
        assertEquals("SUSPICIOUS", captor.getValue().getReportType());
        assertEquals("SUCCESS", captor.getValue().getSubmitStatus());
    }

    // ---- getReportDetail ----

    @Test
    @DisplayName("查询详情-报告不存在-抛异常")
    void getReportDetail_notFound_throws() {
        when(strReportMapper.selectById(100L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getReportDetail(100L));
    }
}
