package com.insurance.aml.module.reporting.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报告提交日志实体
 * 记录报告提交的详细日志，用于重试和审计
 */
@Data
@TableName("t_report_submit_log")
public class ReportSubmitLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 报告类型
     * LARGE_TXN-大额交易报告, SUSPICIOUS-可疑交易报告, ANNUAL-年度报告
     */
    private String reportType;

    /**
     * 报告ID
     */
    private Long reportId;

    /**
     * 提交时间
     */
    private LocalDateTime submitTime;

    /**
     * 提交状态
     * SUCCESS-成功, FAILED-失败
     */
    private String submitStatus;

    /**
     * 请求数据
     */
    private String requestData;

    /**
     * 响应数据
     */
    private String responseData;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 已重试次数（默认0）
     */
    private int retryCount = 0;

    /**
     * 最大重试次数（默认3）
     */
    private int maxRetries = 3;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}
