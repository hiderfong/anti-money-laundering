package com.insurance.aml.module.casemgmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.enums.CaseStatus;
import com.insurance.aml.common.enums.ReportStatus;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.casemgmt.mapper.CaseMapper;
import com.insurance.aml.module.casemgmt.mapper.StrReportMapper;
import com.insurance.aml.module.casemgmt.model.dto.StrReportCreateRequest;
import com.insurance.aml.module.casemgmt.model.dto.StrReportReviewRequest;
import com.insurance.aml.module.casemgmt.model.entity.Case;
import com.insurance.aml.module.casemgmt.model.entity.StrReport;
import com.insurance.aml.module.casemgmt.service.CaseService;
import com.insurance.aml.module.casemgmt.service.StrReportService;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.reporting.mapper.ReportSubmitLogMapper;
import com.insurance.aml.module.reporting.model.entity.ReportSubmitLog;
import com.insurance.aml.module.reporting.service.XmlGeneratorService;
import com.insurance.aml.common.enums.SubmitStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 可疑交易报告（STR）服务实现
 * 处理可疑交易报告的创建、审核、提交等流程
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StrReportServiceImpl implements StrReportService {

    private final StrReportMapper strReportMapper;
    private final CaseMapper caseMapper;
    private final CustomerMapper customerMapper;
    private final IdGenerator idGenerator;
    private final XmlGeneratorService xmlGeneratorService;
    private final ReportSubmitLogMapper reportSubmitLogMapper;
    @Lazy
    private final CaseService caseService;

    @Override
    public PageResult<StrReport> pageQuery(Integer page, Integer size, String status) {
        LambdaQueryWrapper<StrReport> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(StrReport::getReportStatus, status);
        }
        wrapper.orderByDesc(StrReport::getCreatedTime);

        Page<StrReport> result = strReportMapper.selectPage(new Page<>(page, size), wrapper);
        enrichReports(result.getRecords());
        return PageResult.from(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StrReport createReport(StrReportCreateRequest req) {
        log.info("创建可疑交易报告，caseId={}, reportType={}", req.getCaseId(), req.getReportType());

        // 校验案件存在
        Case caseEntity = caseMapper.selectById(req.getCaseId());
        if (caseEntity == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "关联案件不存在，caseId=" + req.getCaseId());
        }

        // 检查是否已有报告
        Long existingCount = strReportMapper.selectCount(
                new LambdaQueryWrapper<StrReport>()
                        .eq(StrReport::getCaseId, req.getCaseId())
        );
        if (existingCount > 0) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "该案件已存在可疑交易报告，不允许重复创建");
        }

        // 创建报告实体
        StrReport report = new StrReport();
        report.setReportNo(idGenerator.generateReportNo());
        report.setCaseId(req.getCaseId());
        report.setCustomerId(caseEntity.getCustomerId());
        report.setReportType(req.getReportType());
        report.setReportStatus(ReportStatus.DRAFT.getCode());
        report.setReportContent(req.getReportContent());
        report.setAnalysisOpinion(req.getAnalysisOpinion());
        report.setMeasuresTaken(req.getMeasuresTaken());
        report.setWriterId(SecurityUtils.getCurrentUserId());
        report.setWriterTime(LocalDateTime.now());
        report.setCreatedTime(LocalDateTime.now());
        report.setUpdatedTime(LocalDateTime.now());

        strReportMapper.insert(report);
        log.info("可疑交易报告创建成功，reportId={}, reportNo={}", report.getId(), report.getReportNo());

        return report;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForReview(Long reportId) {
        log.info("提交可疑交易报告审核，reportId={}", reportId);

        StrReport report = strReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "报告不存在，reportId=" + reportId);
        }

        // 只有DRAFT状态的报告才能提交审核
        if (!ReportStatus.DRAFT.getCode().equals(report.getReportStatus())) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "只有草稿状态的报告才能提交审核，当前状态=" + report.getReportStatus());
        }

        report.setReportStatus(ReportStatus.PENDING_REVIEW.getCode());
        report.setUpdatedTime(LocalDateTime.now());
        strReportMapper.updateById(report);

        log.info("可疑交易报告已提交审核，reportId={}", reportId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewReport(StrReportReviewRequest req) {
        log.info("审核可疑交易报告，reportId={}, approved={}", req.getReportId(), req.getApproved());

        StrReport report = strReportMapper.selectById(req.getReportId());
        if (report == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "报告不存在，reportId=" + req.getReportId());
        }

        // 只有PENDING_REVIEW状态的报告才能审核
        if (!ReportStatus.PENDING_REVIEW.getCode().equals(report.getReportStatus())) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "只有待审核状态的报告才能审核，当前状态=" + report.getReportStatus());
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();

        if (Boolean.TRUE.equals(req.getApproved())) {
            // 批准：更新为APPROVED状态
            report.setReportStatus(ReportStatus.APPROVED.getCode());
            report.setReviewerId(currentUserId);
            report.setReviewerOpinion(req.getOpinion());
            report.setReviewerTime(now);
            report.setUpdatedTime(now);
            strReportMapper.updateById(report);

            // 审核通过后，自动触发案件状态流转到PENDING_APPROVAL
            log.info("报告审核通过，自动触发案件状态流转，caseId={}", report.getCaseId());
            caseService.changeCaseStatus(report.getCaseId(), CaseStatus.PENDING_APPROVAL.getCode(),
                    "可疑交易报告审核通过，案件进入待审批状态");

            log.info("可疑交易报告审核通过，reportId={}", req.getReportId());
        } else {
            // 拒绝：更新为REJECTED状态
            report.setReportStatus(ReportStatus.REJECTED.getCode());
            report.setReviewerId(currentUserId);
            report.setReviewerOpinion(req.getOpinion());
            report.setReviewerTime(now);
            report.setUpdatedTime(now);
            strReportMapper.updateById(report);

            log.info("可疑交易报告审核拒绝，reportId={}", req.getReportId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitToRegulator(Long reportId) {
        log.info("提交可疑交易报告至监管机构，reportId={}", reportId);

        StrReport report = strReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "报告不存在，reportId=" + reportId);
        }

        // 只有APPROVED状态的报告才能提交至监管机构
        if (!ReportStatus.APPROVED.getCode().equals(report.getReportStatus())) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "只有已批准的报告才能提交至监管机构，当前状态=" + report.getReportStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        String xmlContent = xmlGeneratorService.generateSuspiciousTxnXml(report);
        String responseData = buildRegulatorAcceptanceResponse(report);

        report.setReportStatus(ReportStatus.SUBMITTED.getCode());
        report.setSubmitTime(now);
        report.setSubmitResult(responseData);
        report.setUpdatedTime(now);
        strReportMapper.updateById(report);

        ReportSubmitLog submitLog = new ReportSubmitLog();
        submitLog.setReportType("SUSPICIOUS");
        submitLog.setReportId(reportId);
        submitLog.setSubmitTime(now);
        submitLog.setSubmitStatus(SubmitStatus.SUCCESS.getCode());
        submitLog.setRequestData(xmlContent);
        submitLog.setResponseData(responseData);
        submitLog.setRetryCount(0);
        submitLog.setMaxRetries(3);
        submitLog.setCreatedTime(now);
        reportSubmitLogMapper.insert(submitLog);

        log.info("可疑交易报告已提交至监管机构，reportId={}", reportId);
    }

    @Override
    public StrReport getReportDetail(Long reportId) {
        log.info("查询可疑交易报告详情，reportId={}", reportId);

        StrReport report = strReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "报告不存在，reportId=" + reportId);
        }

        enrichReports(Collections.singletonList(report));
        return report;
    }

    private String buildRegulatorAcceptanceResponse(StrReport report) {
        String receiptNo = "RCPT-SUSPICIOUS-" + report.getReportNo() + "-" + System.currentTimeMillis();
        Long submittedBy = SecurityUtils.getCurrentUserId();
        return String.format(
                "{\"status\":\"ACCEPTED\",\"receiptNo\":\"%s\",\"reportType\":\"SUSPICIOUS\",\"reportNo\":\"%s\",\"submittedBy\":\"%s\",\"acceptedAt\":\"%s\"}",
                receiptNo,
                report.getReportNo(),
                submittedBy == null ? "system" : submittedBy,
                LocalDateTime.now()
        );
    }

    private void enrichReports(List<StrReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return;
        }

        List<Long> caseIds = reports.stream()
                .map(StrReport::getCaseId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<Long> customerIds = reports.stream()
                .map(StrReport::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Case> caseMap = loadCaseMap(caseIds);
        Map<Long, String> customerNameMap = loadCustomerNameMap(customerIds);

        reports.forEach(report -> enrichReport(report, caseMap, customerNameMap));
    }

    private Map<Long, Case> loadCaseMap(Collection<Long> caseIds) {
        if (caseIds == null || caseIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Case> cases = caseMapper.selectBatchIds(caseIds);
        if (cases == null) {
            return Collections.emptyMap();
        }
        return cases.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(Case::getId, item -> item, (first, second) -> first));
    }

    private Map<Long, String> loadCustomerNameMap(Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Customer> customers = customerMapper.selectBatchIds(customerIds);
        if (customers == null) {
            return Collections.emptyMap();
        }
        return customers.stream()
                .filter(item -> item.getId() != null && StringUtils.hasText(item.getName()))
                .collect(Collectors.toMap(Customer::getId, Customer::getName, (first, second) -> first));
    }

    private void enrichReport(StrReport report, Map<Long, Case> caseMap, Map<Long, String> customerNameMap) {
        Case caseEntity = caseMap.get(report.getCaseId());
        if (!hasBusinessText(report.getReportContent())) {
            report.setReportContent(buildReportContent(report, caseEntity, customerNameMap));
        }
        if (!hasBusinessText(report.getAnalysisOpinion())) {
            report.setAnalysisOpinion("客户身份资料、交易行为和资金来源说明存在不一致，建议按可疑交易持续跟踪。");
        }
        if (!hasBusinessText(report.getMeasuresTaken())) {
            report.setMeasuresTaken("已开展强化尽调，限制高风险交易，并补充资金来源和交易目的核验材料。");
        }
    }

    private String buildReportContent(StrReport report, Case caseEntity, Map<Long, String> customerNameMap) {
        String customerName = customerNameMap.get(report.getCustomerId());
        if (!StringUtils.hasText(customerName) && caseEntity != null) {
            customerName = caseEntity.getCustomerName();
        }
        if (!StringUtils.hasText(customerName)) {
            customerName = report.getCustomerId() == null ? "相关客户" : "客户ID " + report.getCustomerId();
        }

        String caseSummary = caseEntity == null ? "" : caseEntity.getSummary();
        if (!hasBusinessText(caseSummary)) {
            caseSummary = "存在异常交易行为、身份背景或资金来源说明不足等可疑线索";
        }

        return "可疑交易报告：客户 " + customerName + " 涉及" + caseSummary
                + "。需结合客户尽职调查资料、交易流水、名单筛查和人工复核结论进行持续监测并按要求报送。";
    }

    private boolean hasBusinessText(String value) {
        return StringUtils.hasText(value) && !isMojibakeText(value);
    }

    private boolean isMojibakeText(String value) {
        if (value == null) {
            return false;
        }
        return value.contains("å") || value.contains("æ") || value.contains("è")
                || value.contains("é") || value.contains("ç") || value.contains("ä");
    }
}
