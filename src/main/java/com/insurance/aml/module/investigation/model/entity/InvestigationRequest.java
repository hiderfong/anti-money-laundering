package com.insurance.aml.module.investigation.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 反洗钱调查协查请求。
 */
@Data
@TableName("t_investigation_request")
public class InvestigationRequest extends BaseEntity {

    private String requestNo;
    private String authorityName;
    private String requestType;
    private String documentNo;
    private Long customerId;
    private String customerName;
    private Long relatedCaseId;
    private String priority;
    private LocalDate receivedDate;
    private LocalDate dueDate;
    private String status;
    private String handler;
    private String summary;
    private String responseSummary;
    private LocalDateTime completedTime;
    private LocalDateTime closedTime;
}
