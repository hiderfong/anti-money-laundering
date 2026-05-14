package com.insurance.aml.module.prevention.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDate;

/**
 * 特别预防措施。
 */
@Data
@TableName("t_special_measure")
public class SpecialMeasure extends BaseEntity {

    private String measureNo;
    private Long customerId;
    private String customerName;
    private String measureType;
    private String triggerType;
    private Long relatedResultId;
    private Long relatedAlertId;
    private String controlLevel;
    private String measureContent;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String decisionReason;
    private String closedReason;
}
