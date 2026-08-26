package com.insurance.aml.module.reporting.gateway;

import lombok.Builder;
import lombok.Data;

/** 交给监管网关适配器的已校验、已签名报文。 */
@Data
@Builder
public class RegulatorySubmissionEnvelope {
    private String submissionNo;
    private String reportType;
    private String reportNo;
    private Integer versionNo;
    private String schemaVersion;
    private String payload;
    private String payloadHash;
    private String signatureAlgorithm;
    private String signatureValue;
}
