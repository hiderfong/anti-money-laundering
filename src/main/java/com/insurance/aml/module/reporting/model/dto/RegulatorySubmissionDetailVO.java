package com.insurance.aml.module.reporting.model.dto;

import com.insurance.aml.module.reporting.model.entity.RegulatoryReceipt;
import com.insurance.aml.module.reporting.model.entity.RegulatorySubmission;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RegulatorySubmissionDetailVO {
    private RegulatorySubmission submission;
    private List<RegulatoryReceipt> receipts;
}
