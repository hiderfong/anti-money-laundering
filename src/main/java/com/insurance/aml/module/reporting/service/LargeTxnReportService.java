package com.insurance.aml.module.reporting.service;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.reporting.model.dto.LargeTxnReportQueryRequest;
import com.insurance.aml.module.reporting.model.dto.LargeTxnReportVO;
import com.insurance.aml.module.reporting.model.entity.LargeTxnReport;

/**
 * 大额交易报告服务接口
 */
public interface LargeTxnReportService {

    /**
     * 生成大额交易报告
     *
     * @param transactionId 交易ID
     * @return 报告实体
     */
    LargeTxnReport generateReport(Long transactionId);

    /**
     * 审核报告
     *
     * @param reportId   报告ID
     * @param reviewedBy 审核人
     */
    void reviewReport(Long reportId, String reviewedBy);

    /**
     * 生成XML报文
     *
     * @param reportId 报告ID
     * @return XML内容
     */
    String generateXml(Long reportId);

    /**
     * 提交报告至监管机构
     *
     * @param reportId 报告ID
     */
    void submitReport(Long reportId);

    /**
     * 分页查询报告
     *
     * @param req 查询请求
     * @return 分页结果
     */
    PageResult<LargeTxnReportVO> pageQueryReports(LargeTxnReportQueryRequest req);

    /**
     * 重试失败的提交
     */
    void retryFailedSubmissions();
}
