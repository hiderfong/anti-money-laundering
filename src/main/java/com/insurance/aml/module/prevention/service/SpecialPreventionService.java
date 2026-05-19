package com.insurance.aml.module.prevention.service;

import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.casemgmt.model.entity.Case;
import com.insurance.aml.module.prevention.model.dto.*;
import com.insurance.aml.module.prevention.model.entity.FreezeSeizureDeduction;
import com.insurance.aml.module.prevention.model.entity.RetrospectiveScreeningJob;
import com.insurance.aml.module.prevention.model.entity.SpecialMeasure;
import com.insurance.aml.module.prevention.model.entity.WatchlistUpdateJob;

/**
 * 特别预防措施中心服务。
 */
public interface SpecialPreventionService {

    SpecialPreventionOverviewVO overview();

    WatchlistUpdateJob createWatchlistUpdateJob(WatchlistSyncRequest request);

    PageResult<WatchlistUpdateJob> pageWatchlistUpdateJobs(PageQuery pageQuery, String status);

    RetrospectiveScreeningJob createRetrospectiveJob(RetrospectiveScreeningJobRequest request);

    PageResult<RetrospectiveScreeningJob> pageRetrospectiveJobs(PageQuery pageQuery, String status);

    SpecialMeasure createSpecialMeasure(SpecialMeasureRequest request);

    PageResult<SpecialMeasure> pageSpecialMeasures(PageQuery pageQuery, Long customerId, String status);

    void updateSpecialMeasureStatus(Long id, String status, String reason);

    FreezeSeizureDeduction createFreezeRecord(FreezeSeizureDeductionRequest request);

    PageResult<FreezeSeizureDeduction> pageFreezeRecords(PageQuery pageQuery, Long customerId, String status);

    void updateFreezeRecordStatus(Long id, String status, String remark);

    Alert escalateScreeningResultToAlert(Long resultId, String reason);

    Case createCaseFromScreeningResult(Long resultId, String reason);
}
