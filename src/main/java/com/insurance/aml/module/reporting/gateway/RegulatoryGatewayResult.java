package com.insurance.aml.module.reporting.gateway;

import lombok.Builder;
import lombok.Data;

/** 监管网关传输或回执查询结果。 */
@Data
@Builder
public class RegulatoryGatewayResult {
    private boolean transmitted;
    private String externalRequestId;
    private String receiptStatus;
    private String receiptNo;
    private String receiptCode;
    private String receiptMessage;
    private String receiptPayload;
    private String errorMessage;
}
