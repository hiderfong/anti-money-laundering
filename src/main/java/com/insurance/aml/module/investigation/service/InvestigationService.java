package com.insurance.aml.module.investigation.service;

import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.investigation.model.dto.InvestigationActionRequest;
import com.insurance.aml.module.investigation.model.dto.InvestigationOverviewVO;
import com.insurance.aml.module.investigation.model.dto.InvestigationRequestCreateRequest;
import com.insurance.aml.module.investigation.model.dto.InvestigationStatusUpdateRequest;
import com.insurance.aml.module.investigation.model.entity.InvestigationAction;
import com.insurance.aml.module.investigation.model.entity.InvestigationRequest;

public interface InvestigationService {

    InvestigationOverviewVO overview();

    InvestigationRequest createRequest(InvestigationRequestCreateRequest request);

    PageResult<InvestigationRequest> pageRequests(PageQuery pageQuery, String status, String requestType, String authorityName);

    void updateStatus(Long id, InvestigationStatusUpdateRequest request);

    InvestigationAction addAction(Long requestId, InvestigationActionRequest request);

    PageResult<InvestigationAction> pageActions(Long requestId, PageQuery pageQuery);
}
