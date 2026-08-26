package com.insurance.aml.module.reporting.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegulatorySubmissionOverviewVO {
    private long totalSubmissions;
    private long pendingReceipts;
    private long acceptedSubmissions;
    private long rejectedSubmissions;
    private long failedSubmissions;
    private long resubmissions;
    private double acceptanceRate;
}
