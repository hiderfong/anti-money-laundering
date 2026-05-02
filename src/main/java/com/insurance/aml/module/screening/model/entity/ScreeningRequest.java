package com.insurance.aml.module.screening.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 筛查请求实体
 * 记录每次制裁名单筛查的请求信息
 */
@Data
@TableName("t_screening_request")
public class ScreeningRequest {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 筛查请求编号
     */
    private String requestNo;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 筛查类型：CUSTOMER_ONBOARD-开户、INFO_CHANGE-信息变更、TRANSACTION-交易、PERIODIC-定期、BATCH-批量
     */
    private String screeningType;

    /**
     * 请求来源：KYC-了解你的客户、MONITORING-监控、SCHEDULED-定时任务、MANUAL-人工触发
     */
    private String requestSource;

    /**
     * 请求数据（JSON格式）
     */
    private String requestData;

    /**
     * 扫描总数
     */
    private Integer totalScanned;

    /**
     * 命中数
     */
    private Integer totalHit;

    /**
     * 状态：PROCESSING-处理中、COMPLETED-已完成、FAILED-失败
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 完成时间
     */
    private LocalDateTime completedTime;
}
