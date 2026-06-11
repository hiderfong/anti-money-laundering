package com.insurance.aml.module.reporting.service;

import com.insurance.aml.common.enums.ReportStatus;
import com.insurance.aml.common.enums.SubmitStatus;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.reporting.mapper.LargeTxnReportMapper;
import com.insurance.aml.module.reporting.mapper.ReportSubmitLogMapper;
import com.insurance.aml.module.reporting.model.entity.LargeTxnReport;
import com.insurance.aml.module.reporting.model.entity.ReportSubmitLog;
import com.insurance.aml.module.reporting.service.impl.LargeTxnReportServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CTR（大额交易报告）服务单元测试。
 * 覆盖 generate→review→submit 状态机与 generateXml / retry 守卫。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CTR大额交易报告服务测试")
class LargeTxnReportServiceImplTest {

    @Mock LargeTxnReportMapper largeTxnReportMapper;
    @Mock ReportSubmitLogMapper reportSubmitLogMapper;
    @Mock TransactionMapper transactionMapper;
    @Mock CustomerMapper customerMapper;
    @Mock IdGenerator idGenerator;
    @Mock XmlGeneratorService xmlGeneratorService;

    @InjectMocks LargeTxnReportServiceImpl service;

    private Transaction txn() {
        Transaction t = new Transaction();
        t.setId(5L);
        t.setCustomerId(9L);
        return t;
    }

    private Customer customer() {
        Customer c = new Customer();
        c.setId(9L);
        c.setName("测试客户");
        return c;
    }

    private LargeTxnReport reportWithStatus(String status) {
        LargeTxnReport r = new LargeTxnReport();
        r.setId(200L);
        r.setReportNo("CTR-200");
        r.setTransactionId(5L);
        r.setCustomerId(9L);
        r.setReportStatus(status);
        return r;
    }

    // ---- generateReport ----

    @Test
    @DisplayName("生成报告-交易不存在-抛异常")
    void generateReport_transactionNotFound_throws() {
        when(transactionMapper.selectById(5L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.generateReport(5L));
        verify(largeTxnReportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("生成报告-客户不存在-抛异常")
    void generateReport_customerNotFound_throws() {
        when(transactionMapper.selectById(5L)).thenReturn(txn());
        when(customerMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.generateReport(5L));
        verify(largeTxnReportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("生成报告-正常-DRAFT状态")
    void generateReport_happyPath_draft() {
        when(transactionMapper.selectById(5L)).thenReturn(txn());
        when(customerMapper.selectById(9L)).thenReturn(customer());
        when(idGenerator.generateReportNo()).thenReturn("CTR-NEW");

        LargeTxnReport result = service.generateReport(5L);

        assertEquals(ReportStatus.DRAFT.getCode(), result.getReportStatus());
        assertEquals(9L, result.getCustomerId());
        assertEquals("CTR-NEW", result.getReportNo());
        verify(largeTxnReportMapper, times(1)).insert(any());
    }

    // ---- reviewReport ----

    @Test
    @DisplayName("审核-报告不存在-抛异常")
    void reviewReport_notFound_throws() {
        when(largeTxnReportMapper.selectById(200L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.reviewReport(200L, "reviewer"));
    }

    @Test
    @DisplayName("审核-非DRAFT状态-抛异常")
    void reviewReport_nonDraft_throws() {
        when(largeTxnReportMapper.selectById(200L)).thenReturn(reportWithStatus(ReportStatus.SUBMITTED.getCode()));
        assertThrows(BusinessException.class, () -> service.reviewReport(200L, "reviewer"));
        verify(largeTxnReportMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("审核-DRAFT-转REVIEWED并记录审核人")
    void reviewReport_draft_transitionsToReviewed() {
        LargeTxnReport r = reportWithStatus(ReportStatus.DRAFT.getCode());
        when(largeTxnReportMapper.selectById(200L)).thenReturn(r);

        service.reviewReport(200L, "reviewer-a");

        assertEquals(ReportStatus.REVIEWED.getCode(), r.getReportStatus());
        assertEquals("reviewer-a", r.getReviewedBy());
        verify(largeTxnReportMapper, times(1)).updateById(r);
    }

    // ---- generateXml ----

    @Test
    @DisplayName("生成XML-报告不存在-抛异常")
    void generateXml_notFound_throws() {
        when(largeTxnReportMapper.selectById(200L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.generateXml(200L));
    }

    @Test
    @DisplayName("生成XML-关联交易缺失-抛异常")
    void generateXml_missingTransaction_throws() {
        when(largeTxnReportMapper.selectById(200L)).thenReturn(reportWithStatus(ReportStatus.REVIEWED.getCode()));
        when(transactionMapper.selectById(5L)).thenReturn(null);
        when(customerMapper.selectById(9L)).thenReturn(customer());
        assertThrows(BusinessException.class, () -> service.generateXml(200L));
    }

    @Test
    @DisplayName("生成XML-关联客户缺失-抛异常")
    void generateXml_missingCustomer_throws() {
        when(largeTxnReportMapper.selectById(200L)).thenReturn(reportWithStatus(ReportStatus.REVIEWED.getCode()));
        when(transactionMapper.selectById(5L)).thenReturn(txn());
        when(customerMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.generateXml(200L));
    }

    @Test
    @DisplayName("生成XML-正常-委派XmlGeneratorService")
    void generateXml_happyPath_delegates() {
        LargeTxnReport r = reportWithStatus(ReportStatus.REVIEWED.getCode());
        Transaction t = txn();
        Customer c = customer();
        when(largeTxnReportMapper.selectById(200L)).thenReturn(r);
        when(transactionMapper.selectById(5L)).thenReturn(t);
        when(customerMapper.selectById(9L)).thenReturn(c);
        when(xmlGeneratorService.generateLargeTxnXml(r, c, t)).thenReturn("<ctr/>");

        String xml = service.generateXml(200L);

        assertEquals("<ctr/>", xml);
        verify(xmlGeneratorService, times(1)).generateLargeTxnXml(r, c, t);
    }

    // ---- submitReport ----

    @Test
    @DisplayName("提交-报告不存在-抛异常")
    void submitReport_notFound_throws() {
        when(largeTxnReportMapper.selectById(200L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.submitReport(200L));
    }

    @Test
    @DisplayName("提交-非REVIEWED状态-拒绝(合规关键)")
    void submitReport_nonReviewed_throws() {
        when(largeTxnReportMapper.selectById(200L)).thenReturn(reportWithStatus(ReportStatus.DRAFT.getCode()));
        assertThrows(BusinessException.class, () -> service.submitReport(200L));
        verify(reportSubmitLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("提交-REVIEWED-转SUBMITTED并写成功日志")
    void submitReport_reviewed_submitsAndLogs() {
        LargeTxnReport r = reportWithStatus(ReportStatus.REVIEWED.getCode());
        when(largeTxnReportMapper.selectById(200L)).thenReturn(r);
        when(transactionMapper.selectById(5L)).thenReturn(txn());
        when(customerMapper.selectById(9L)).thenReturn(customer());
        when(xmlGeneratorService.generateLargeTxnXml(any(), any(), any())).thenReturn("<ctr/>");

        service.submitReport(200L);

        assertEquals(ReportStatus.SUBMITTED.getCode(), r.getReportStatus());
        ArgumentCaptor<ReportSubmitLog> captor = ArgumentCaptor.forClass(ReportSubmitLog.class);
        verify(reportSubmitLogMapper, times(1)).insert(captor.capture());
        assertEquals("LARGE_TXN", captor.getValue().getReportType());
        assertEquals(SubmitStatus.SUCCESS.getCode(), captor.getValue().getSubmitStatus());
    }

    // ---- retryFailedSubmissions ----

    @Test
    @DisplayName("重试-上限内失败日志-重试成功并标记SUCCESS")
    void retryFailedSubmissions_retriesWithinLimit() {
        ReportSubmitLog failed = new ReportSubmitLog();
        failed.setReportId(200L);
        failed.setSubmitStatus(SubmitStatus.FAILED.getCode());
        failed.setRetryCount(0);
        failed.setMaxRetries(3);
        when(reportSubmitLogMapper.selectList(any())).thenReturn(List.of(failed));
        when(largeTxnReportMapper.selectById(200L)).thenReturn(reportWithStatus(ReportStatus.REVIEWED.getCode()));
        when(transactionMapper.selectById(5L)).thenReturn(txn());
        when(customerMapper.selectById(9L)).thenReturn(customer());
        when(xmlGeneratorService.generateLargeTxnXml(any(), any(), any())).thenReturn("<ctr/>");

        service.retryFailedSubmissions();

        assertEquals(SubmitStatus.SUCCESS.getCode(), failed.getSubmitStatus());
        assertEquals(1, failed.getRetryCount());
        verify(reportSubmitLogMapper, atLeast(2)).updateById(any());
    }
}
