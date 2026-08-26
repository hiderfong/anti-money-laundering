package com.insurance.aml.module.reporting.service;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.reporting.model.dto.RegulatoryReceiptRequest;
import com.insurance.aml.module.reporting.model.dto.RegulatoryResubmitRequest;
import com.insurance.aml.module.reporting.model.dto.RegulatorySubmissionDetailVO;
import com.insurance.aml.module.reporting.model.dto.RegulatorySubmissionOverviewVO;
import com.insurance.aml.module.reporting.model.dto.RegulatorySubmissionQuery;
import com.insurance.aml.module.reporting.model.entity.RegulatorySubmission;

public interface RegulatorySubmissionService {
    RegulatorySubmissionOverviewVO overview();

    PageResult<RegulatorySubmission> page(RegulatorySubmissionQuery query);

    RegulatorySubmissionDetailVO detail(Long id);

    RegulatorySubmission submitInitial(String reportType, Long reportId, Long connectorId);

    RegulatorySubmission resubmit(Long originalSubmissionId, RegulatoryResubmitRequest request);

    RegulatorySubmission pollReceipt(Long submissionId);

    RegulatorySubmission applyReceipt(Long submissionId, RegulatoryReceiptRequest request, String source);
}
