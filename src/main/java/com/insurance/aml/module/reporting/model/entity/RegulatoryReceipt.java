package com.insurance.aml.module.reporting.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监管回执历史。重复回调也独立留痕，便于审计最终状态的来源。
 */
@Data
@TableName("t_regulatory_receipt")
public class RegulatoryReceipt extends BaseEntity {
    private Long submissionId;
    private String receiptNo;
    private String receiptStatus;
    private String receiptCode;
    private String receiptMessage;
    private String receiptPayload;
    private LocalDateTime receivedTime;
    private String receiptSource;
}
