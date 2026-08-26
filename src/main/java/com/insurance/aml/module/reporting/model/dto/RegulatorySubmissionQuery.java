package com.insurance.aml.module.reporting.model.dto;

import com.insurance.aml.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RegulatorySubmissionQuery extends PageQuery {
    private String keyword;
    private String reportType;
    private String status;
    private String receiptStatus;
    private Long connectorId;
}
