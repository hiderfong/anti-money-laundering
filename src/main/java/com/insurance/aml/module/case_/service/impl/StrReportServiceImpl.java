package com.insurance.aml.module.case_.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.enums.CaseStatus;
import com.insurance.aml.common.enums.ReportStatus;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.case_.mapper.CaseMapper;
import com.insurance.aml.module.case_.mapper.StrReportMapper;
import com.insurance.aml.module.case_.model.dto.StrReportCreateRequest;
import com.insurance.aml.module.case_.model.dto.StrReportReviewRequest;
import com.insurance.aml.module.case_.model.entity.Case;
import com.insurance.aml.module.case_.model.entity.StrReport;
import com.insurance.aml.module.case_.service.CaseService;
import com.insurance.aml.module.case_.service.StrReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
    private final IdGenerator idGenerator;
    @Lazy
    private final CaseService caseService;

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
        report.setReportStatus(ReportStatus.SUBMITTED.getCode());
        report.setSubmitTime(now);
        // 实际XML生成和提交将在reporting模块中实现
        report.setSubmitResult("提交成功");
        report.setUpdatedTime(now);
        strReportMapper.updateById(report);

        log.info("可疑交易报告已提交至监管机构，reportId={}", reportId);
    }

    @Override
    public StrReport getReportDetail(Long reportId) {
        log.info("查询可疑交易报告详情，reportId={}", reportId);

        StrReport report = strReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "报告不存在，reportId=" + reportId);
        }

        return report;
    }
}
