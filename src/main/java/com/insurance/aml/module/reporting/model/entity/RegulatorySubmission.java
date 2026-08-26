package com.insurance.aml.module.reporting.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单次监管报送版本。每次重报新建记录，保留原始报文、签名和回执链路。
 */
@Data
@TableName("t_regulatory_submission")
public class RegulatorySubmission extends BaseEntity {
    private String submissionNo;
    private String reportType;
    private Long reportId;
    private String reportNo;
    private Integer versionNo;
    private Long parentSubmissionId;
    private Long connectorId;
    private String status;
    private String schemaVersion;
    private String payloadFormat;
    private String payloadContent;
    private String payloadHash;
    private String signatureAlgorithm;
    private String signatureValue;
    private String externalRequestId;
    private String submittedBy;
    private LocalDateTime submittedTime;
    private LocalDateTime completedTime;
    private String receiptStatus;
    private String receiptNo;
    private LocalDateTime receiptTime;
    private String returnCode;
    private String returnMessage;
    private String correctionNote;
    private String failureStage;
    private String errorMessage;
    private Integer retryCount;

    @TableField(exist = false)
    private String connectorName;
}
