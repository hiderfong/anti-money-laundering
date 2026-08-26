package com.insurance.aml.module.reporting.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegulatoryReceiptRequest {
    @NotBlank(message = "回执状态不能为空")
    private String receiptStatus;
    private String receiptNo;
    private String receiptCode;
    private String receiptMessage;
    private String receiptPayload;
}
