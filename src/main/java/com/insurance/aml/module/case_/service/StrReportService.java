package com.insurance.aml.module.case_.service;

import com.insurance.aml.module.case_.model.dto.StrReportCreateRequest;
import com.insurance.aml.module.case_.model.dto.StrReportReviewRequest;
import com.insurance.aml.module.case_.model.entity.StrReport;

/**
 * 可疑交易报告（STR）服务接口
 * 提供可疑交易报告的创建、审核、提交等流程管理
 */
public interface StrReportService {

    /**
     * 创建可疑交易报告
     * 生成报告编号，状态设为DRAFT
     *
     * @param req 创建请求
     * @return 创建的报告实体
     */
    StrReport createReport(StrReportCreateRequest req);

    /**
     * 提交报告审核
     * 将DRAFT状态的报告提交审核，状态变更为PENDING_REVIEW
     *
     * @param reportId 报告ID
     */
    void submitForReview(Long reportId);

    /**
     * 审核报告
     * 批准则状态变更为APPROVED，并自动触发案件状态流转到PENDING_APPROVAL
     * 拒绝则状态变更为REJECTED
     *
     * @param req 审核请求（含报告ID、审核结果、审核意见）
     */
    void reviewReport(StrReportReviewRequest req);

    /**
     * 提交报告至监管机构
     * 将已批准的报告提交给监管机构，状态变更为SUBMITTED
     *
     * @param reportId 报告ID
     */
    void submitToRegulator(Long reportId);

    /**
     * 获取报告详情
     *
     * @param reportId 报告ID
     * @return 报告实体
     */
    StrReport getReportDetail(Long reportId);
}
