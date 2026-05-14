package com.insurance.aml.module.prevention.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 查冻扣信息。
 */
@Data
@TableName("t_freeze_seizure_deduction")
public class FreezeSeizureDeduction extends BaseEntity {

    private String recordNo;
    private Long customerId;
    private String customerName;
    private String authorityName;
    private String documentNo;
    private String actionType;
    private BigDecimal amount;
    private String currency;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String status;
    private String handler;
    private String remark;
}
