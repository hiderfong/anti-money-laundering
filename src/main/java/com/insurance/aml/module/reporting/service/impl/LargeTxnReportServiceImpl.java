package com.insurance.aml.module.reporting.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.reporting.mapper.LargeTxnReportMapper;
import com.insurance.aml.module.reporting.mapper.ReportSubmitLogMapper;
import com.insurance.aml.module.reporting.model.dto.LargeTxnReportQueryRequest;
import com.insurance.aml.module.reporting.model.dto.LargeTxnReportVO;
import com.insurance.aml.module.reporting.model.entity.LargeTxnReport;
import com.insurance.aml.module.reporting.model.entity.ReportSubmitLog;
import com.insurance.aml.module.reporting.service.LargeTxnReportService;
import com.insurance.aml.module.reporting.service.XmlGeneratorService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 大额交易报告服务实现
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LargeTxnReportServiceImpl implements LargeTxnReportService {
    private final LargeTxnReportMapper largeTxnReportMapper;
    private final ReportSubmitLogMapper reportSubmitLogMapper;
    private final TransactionMapper transactionMapper;
    private final CustomerMapper customerMapper;
    private final IdGenerator idGenerator;
    private final XmlGeneratorService xmlGeneratorService;

    /**
     * 生成大额交易报告
     * 根据交易ID加载交易和客户信息，创建报告草稿
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LargeTxnReport generateReport(Long transactionId) {
        log.info("开始生成大额交易报告，交易ID：{}", transactionId);

        // 加载交易信息
        Transaction transaction = transactionMapper.selectById(transactionId);
        if (transaction == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "交易不存在，ID：" + transactionId);
        }

        // 加载客户信息
        Customer customer = customerMapper.selectById(transaction.getCustomerId());
        if (customer == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "客户不存在，ID：" + transaction.getCustomerId());
        }

        // 创建报告实体
        LargeTxnReport report = new LargeTxnReport();
        report.setReportNo(idGenerator.generateReportNo());
        report.setCustomerId(customer.getId());
        report.setCustomerName(customer.getName());
        report.setTransactionId(transaction.getId());
        report.setReportDate(LocalDate.now());
        report.setTransactionTime(transaction.getTransactionTime());
        report.setTransactionType(transaction.getTransactionType());
        report.setAmount(transaction.getAmount());
        report.setCurrency(transaction.getCurrency());
        report.setPaymentMethod(transaction.getPaymentMethod());
        report.setReportStatus("DRAFT");
        report.setCreatedTime(LocalDateTime.now());
        report.setUpdatedTime(LocalDateTime.now());

        // 组装交易对手信息
        String counterpartyInfo = String.format("{\"name\":\"%s\",\"account\":\"%s\",\"bank\":\"%s\"}",
                transaction.getCounterpartyName(),
                transaction.getCounterpartyAccount(),
                transaction.getCounterpartyBank());
        report.setCounterpartyInfo(counterpartyInfo);

        largeTxnReportMapper.insert(report);
        log.info("大额交易报告生成完成，报告编号：{}", report.getReportNo());
        return report;
    }

    /**
     * 审核报告
     * 更新报告状态为已审核，记录审核人和审核时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewReport(Long reportId, String reviewedBy) {
        log.info("审核大额交易报告，报告ID：{}，审核人：{}", reportId, reviewedBy);

        LargeTxnReport report = largeTxnReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在，ID：" + reportId);
        }

        // 只有草稿状态的报告才能审核
        if (!"DRAFT".equals(report.getReportStatus())) {
            throw new BusinessException("只有草稿状态的报告才能审核，当前状态：" + report.getReportStatus());
        }

        report.setReportStatus("REVIEWED");
        report.setReviewedBy(reviewedBy);
        report.setReviewedTime(LocalDateTime.now());
        report.setUpdatedTime(LocalDateTime.now());
        largeTxnReportMapper.updateById(report);

        log.info("大额交易报告审核完成，报告编号：{}", report.getReportNo());
    }

    /**
     * 生成XML报文
     */
    @Override
    public String generateXml(Long reportId) {
        LargeTxnReport report = largeTxnReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在，ID：" + reportId);
        }

        Transaction transaction = transactionMapper.selectById(report.getTransactionId());
        Customer customer = customerMapper.selectById(report.getCustomerId());

        if (transaction == null || customer == null) {
            throw new BusinessException("关联的交易或客户信息不存在");
        }

        return xmlGeneratorService.generateLargeTxnXml(report, customer, transaction);
    }

    /**
     * 提交报告至监管机构
     * 生成XML报文，保存到报告记录，标记为已提交，并记录提交日志
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReport(Long reportId) {
        log.info("提交大额交易报告，报告ID：{}", reportId);

        LargeTxnReport report = largeTxnReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在，ID：" + reportId);
        }

        // 只有已审核状态的报告才能提交
        if (!"REVIEWED".equals(report.getReportStatus())) {
            throw new BusinessException("只有已审核状态的报告才能提交，当前状态：" + report.getReportStatus());
        }

        // 生成XML报文
        String xmlContent = generateXml(reportId);

        // 更新报告状态
        report.setXmlContent(xmlContent);
        report.setReportStatus("SUBMITTED");
        report.setSubmittedBy("system"); // TODO: 从SecurityContext获取当前用户
        report.setSubmittedTime(LocalDateTime.now());
        report.setUpdatedTime(LocalDateTime.now());
        largeTxnReportMapper.updateById(report);

        // 记录提交日志
        ReportSubmitLog submitLog = new ReportSubmitLog();
        submitLog.setReportType("LARGE_TXN");
        submitLog.setReportId(reportId);
        submitLog.setSubmitTime(LocalDateTime.now());
        submitLog.setSubmitStatus("SUCCESS");
        submitLog.setRequestData(xmlContent);
        submitLog.setResponseData("{\"status\":\"ACCEPTED\"}"); // TODO: 替换为实际响应
        submitLog.setRetryCount(0);
        submitLog.setMaxRetries(3);
        submitLog.setCreatedTime(LocalDateTime.now());
        reportSubmitLogMapper.insert(submitLog);

        log.info("大额交易报告提交完成，报告编号：{}", report.getReportNo());
    }

    /**
     * 分页查询报告列表
     */
    @Override
    public PageResult<LargeTxnReportVO> pageQueryReports(LargeTxnReportQueryRequest req) {
        LambdaQueryWrapper<LargeTxnReport> wrapper = new LambdaQueryWrapper<>();

        // 构建查询条件
        if (req.getReportStatus() != null && !req.getReportStatus().isEmpty()) {
            wrapper.eq(LargeTxnReport::getReportStatus, req.getReportStatus());
        }
        if (req.getCustomerId() != null) {
            wrapper.eq(LargeTxnReport::getCustomerId, req.getCustomerId());
        }
        if (req.getStartDate() != null) {
            wrapper.ge(LargeTxnReport::getReportDate, req.getStartDate());
        }
        if (req.getEndDate() != null) {
            wrapper.le(LargeTxnReport::getReportDate, req.getEndDate());
        }

        wrapper.orderByDesc(LargeTxnReport::getCreatedTime);

        // 执行分页查询
        var page = largeTxnReportMapper.selectPage(req.toPage(), wrapper);

        // 转换为VO
        List<LargeTxnReportVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        PageResult result = new PageResult();
        result.setTotal(page.getTotal());
        result.setPage((int) page.getCurrent());
        result.setSize((int) page.getSize());
        result.setList(voList);
        return result;
    }

    /**
     * 重试失败的提交
     * 查询提交失败且未超过最大重试次数的报告，重新提交
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryFailedSubmissions() {
        log.info("开始重试失败的报告提交");

        // 查询失败的提交日志
        LambdaQueryWrapper<ReportSubmitLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportSubmitLog::getSubmitStatus, "FAILED")
                .lt(ReportSubmitLog::getRetryCount, 3); // retry_count < max_retries(默认3)

        List<ReportSubmitLog> failedLogs = reportSubmitLogMapper.selectList(wrapper);
        log.info("找到{}条失败的提交记录需要重试", failedLogs.size());

        for (ReportSubmitLog submitLog : failedLogs) {
            try {
                // 更新重试次数
                submitLog.setRetryCount(submitLog.getRetryCount() + 1);
                submitLog.setNextRetryTime(LocalDateTime.now().plusMinutes(5 * submitLog.getRetryCount()));
                reportSubmitLogMapper.updateById(submitLog);

                // 重新提交报告
                submitReport(submitLog.getReportId());

                // 更新日志状态为成功
                submitLog.setSubmitStatus("SUCCESS");
                submitLog.setErrorMessage(null);
                reportSubmitLogMapper.updateById(submitLog);

                log.info("报告重试提交成功，报告ID：{}", submitLog.getReportId());
            } catch (Exception e) {
                log.error("报告重试提交失败，报告ID：{}，错误：{}", submitLog.getReportId(), e.getMessage());
                submitLog.setErrorMessage(e.getMessage());
                reportSubmitLogMapper.updateById(submitLog);
            }
        }
    }

    /**
     * 实体转换为VO
     */
    private LargeTxnReportVO convertToVO(LargeTxnReport entity) {
        LargeTxnReportVO vo = new LargeTxnReportVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
