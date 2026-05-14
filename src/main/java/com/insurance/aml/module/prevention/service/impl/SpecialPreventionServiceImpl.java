package com.insurance.aml.module.prevention.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.enums.AlertStatus;
import com.insurance.aml.common.enums.RiskLevel;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.alert.mapper.AlertMapper;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.alert.model.entity.AlertRuleDetail;
import com.insurance.aml.module.alert.service.AlertService;
import com.insurance.aml.module.case_.model.dto.CaseCreateRequest;
import com.insurance.aml.module.case_.model.entity.Case;
import com.insurance.aml.module.case_.service.CaseService;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.prevention.mapper.FreezeSeizureDeductionMapper;
import com.insurance.aml.module.prevention.mapper.RetrospectiveScreeningJobMapper;
import com.insurance.aml.module.prevention.mapper.SpecialMeasureMapper;
import com.insurance.aml.module.prevention.mapper.WatchlistUpdateJobMapper;
import com.insurance.aml.module.prevention.model.dto.*;
import com.insurance.aml.module.prevention.model.entity.FreezeSeizureDeduction;
import com.insurance.aml.module.prevention.model.entity.RetrospectiveScreeningJob;
import com.insurance.aml.module.prevention.model.entity.SpecialMeasure;
import com.insurance.aml.module.prevention.model.entity.WatchlistUpdateJob;
import com.insurance.aml.module.screening.mapper.ScreeningResultMapper;
import com.insurance.aml.module.screening.mapper.WatchlistMapper;
import com.insurance.aml.module.screening.mapper.WatchlistSourceMapper;
import com.insurance.aml.module.screening.model.entity.ScreeningResult;
import com.insurance.aml.module.screening.model.entity.Watchlist;
import com.insurance.aml.module.screening.model.entity.WatchlistSource;
import com.insurance.aml.module.prevention.service.SpecialPreventionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 特别预防措施中心服务实现。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SpecialPreventionServiceImpl implements SpecialPreventionService {

    private final WatchlistUpdateJobMapper updateJobMapper;
    private final RetrospectiveScreeningJobMapper retrospectiveJobMapper;
    private final SpecialMeasureMapper specialMeasureMapper;
    private final FreezeSeizureDeductionMapper freezeMapper;
    private final WatchlistSourceMapper watchlistSourceMapper;
    private final WatchlistMapper watchlistMapper;
    private final ScreeningResultMapper screeningResultMapper;
    private final CustomerMapper customerMapper;
    private final AlertService alertService;
    private final AlertMapper alertMapper;
    private final CaseService caseService;
    private final IdGenerator idGenerator;

    @Override
    public SpecialPreventionOverviewVO overview() {
        long activeMeasures = specialMeasureMapper.selectCount(
                new LambdaQueryWrapper<SpecialMeasure>().eq(SpecialMeasure::getStatus, "ACTIVE"));
        long activeFreezeRecords = freezeMapper.selectCount(
                new LambdaQueryWrapper<FreezeSeizureDeduction>().eq(FreezeSeizureDeduction::getStatus, "ACTIVE"));
        long pendingScreeningResults = screeningResultMapper.selectCount(
                new LambdaQueryWrapper<ScreeningResult>().eq(ScreeningResult::getReviewStatus, "PENDING_REVIEW"));
        long activeRetrospectiveJobs = retrospectiveJobMapper.selectCount(
                new LambdaQueryWrapper<RetrospectiveScreeningJob>().in(RetrospectiveScreeningJob::getStatus, List.of("PENDING", "RUNNING")));

        return SpecialPreventionOverviewVO.builder()
                .activeMeasures(activeMeasures)
                .activeFreezeRecords(activeFreezeRecords)
                .pendingScreeningResults(pendingScreeningResults)
                .activeRetrospectiveJobs(activeRetrospectiveJobs)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WatchlistUpdateJob createWatchlistUpdateJob(WatchlistSyncRequest request) {
        WatchlistSource source = null;
        if (request.getSourceId() != null) {
            source = watchlistSourceMapper.selectById(request.getSourceId());
            if (source == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "名单源不存在，id=" + request.getSourceId());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<Watchlist> countWrapper = new LambdaQueryWrapper<>();
        if (request.getSourceId() != null) {
            countWrapper.eq(Watchlist::getSourceId, request.getSourceId());
        }
        long total = watchlistMapper.selectCount(countWrapper);

        WatchlistUpdateJob job = new WatchlistUpdateJob();
        job.setJobNo(idGenerator.generate("WUJ"));
        job.setSourceId(request.getSourceId());
        job.setSourceName(source == null ? "全部名单源" : source.getSourceName());
        job.setUpdateMode(request.getUpdateMode());
        job.setStatus("SUCCESS");
        job.setTotalEntries((int) total);
        job.setAddedCount(0);
        job.setUpdatedCount(0);
        job.setExpiredCount(0);
        job.setStartedTime(now);
        job.setCompletedTime(now);
        updateJobMapper.insert(job);

        if (source != null) {
            source.setLastUpdateTime(now);
            source.setTotalEntries((int) total);
            watchlistSourceMapper.updateById(source);
        }
        return job;
    }

    @Override
    public PageResult<WatchlistUpdateJob> pageWatchlistUpdateJobs(PageQuery pageQuery, String status) {
        LambdaQueryWrapper<WatchlistUpdateJob> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(WatchlistUpdateJob::getStatus, status);
        }
        wrapper.orderByDesc(WatchlistUpdateJob::getCreatedTime);
        IPage<WatchlistUpdateJob> page = updateJobMapper.selectPage(pageQuery.toPage(), wrapper);
        return PageResult.from(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RetrospectiveScreeningJob createRetrospectiveJob(RetrospectiveScreeningJobRequest request) {
        int totalCustomers = countCustomersByScope(request);
        int totalHits = Math.toIntExact(screeningResultMapper.selectCount(
                new LambdaQueryWrapper<ScreeningResult>().in(ScreeningResult::getReviewStatus, List.of("PENDING_REVIEW", "CONFIRMED", "ESCALATED"))));

        LocalDateTime now = LocalDateTime.now();
        RetrospectiveScreeningJob job = new RetrospectiveScreeningJob();
        job.setJobNo(idGenerator.generate("RSJ"));
        job.setJobName(request.getJobName());
        job.setScopeType(request.getScopeType());
        job.setCustomerIds(request.getCustomerIds());
        job.setWatchlistSourceId(request.getWatchlistSourceId());
        job.setStatus("COMPLETED");
        job.setTotalCustomers(totalCustomers);
        job.setProcessedCustomers(totalCustomers);
        job.setTotalHits(totalHits);
        job.setStartedTime(now);
        job.setCompletedTime(now);
        job.setRemark(request.getRemark());
        retrospectiveJobMapper.insert(job);
        return job;
    }

    @Override
    public PageResult<RetrospectiveScreeningJob> pageRetrospectiveJobs(PageQuery pageQuery, String status) {
        LambdaQueryWrapper<RetrospectiveScreeningJob> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(RetrospectiveScreeningJob::getStatus, status);
        }
        wrapper.orderByDesc(RetrospectiveScreeningJob::getCreatedTime);
        IPage<RetrospectiveScreeningJob> page = retrospectiveJobMapper.selectPage(pageQuery.toPage(), wrapper);
        return PageResult.from(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpecialMeasure createSpecialMeasure(SpecialMeasureRequest request) {
        Customer customer = loadCustomer(request.getCustomerId());

        SpecialMeasure measure = new SpecialMeasure();
        measure.setMeasureNo(idGenerator.generate("SPM"));
        measure.setCustomerId(customer.getId());
        measure.setCustomerName(customer.getName());
        measure.setMeasureType(request.getMeasureType());
        measure.setTriggerType(request.getTriggerType());
        measure.setRelatedResultId(request.getRelatedResultId());
        measure.setRelatedAlertId(request.getRelatedAlertId());
        measure.setControlLevel(StringUtils.hasText(request.getControlLevel()) ? request.getControlLevel() : "MEDIUM");
        measure.setMeasureContent(request.getMeasureContent());
        measure.setStartDate(request.getStartDate());
        measure.setEndDate(request.getEndDate());
        measure.setStatus("ACTIVE");
        measure.setDecisionReason(request.getDecisionReason());
        specialMeasureMapper.insert(measure);
        return measure;
    }

    @Override
    public PageResult<SpecialMeasure> pageSpecialMeasures(PageQuery pageQuery, Long customerId, String status) {
        LambdaQueryWrapper<SpecialMeasure> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) {
            wrapper.eq(SpecialMeasure::getCustomerId, customerId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SpecialMeasure::getStatus, status);
        }
        wrapper.orderByDesc(SpecialMeasure::getCreatedTime);
        IPage<SpecialMeasure> page = specialMeasureMapper.selectPage(pageQuery.toPage(), wrapper);
        return PageResult.from(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpecialMeasureStatus(Long id, String status, String reason) {
        SpecialMeasure measure = specialMeasureMapper.selectById(id);
        if (measure == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "特别预防措施不存在，id=" + id);
        }
        measure.setStatus(status);
        measure.setClosedReason(reason);
        specialMeasureMapper.updateById(measure);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FreezeSeizureDeduction createFreezeRecord(FreezeSeizureDeductionRequest request) {
        Customer customer = loadCustomer(request.getCustomerId());

        FreezeSeizureDeduction record = new FreezeSeizureDeduction();
        record.setRecordNo(idGenerator.generate("FSD"));
        record.setCustomerId(customer.getId());
        record.setCustomerName(customer.getName());
        record.setAuthorityName(request.getAuthorityName());
        record.setDocumentNo(request.getDocumentNo());
        record.setActionType(request.getActionType());
        record.setAmount(request.getAmount());
        record.setCurrency(StringUtils.hasText(request.getCurrency()) ? request.getCurrency() : "CNY");
        record.setEffectiveDate(request.getEffectiveDate());
        record.setExpiryDate(request.getExpiryDate());
        record.setStatus("ACTIVE");
        record.setHandler(request.getHandler());
        record.setRemark(request.getRemark());
        freezeMapper.insert(record);
        return record;
    }

    @Override
    public PageResult<FreezeSeizureDeduction> pageFreezeRecords(PageQuery pageQuery, Long customerId, String status) {
        LambdaQueryWrapper<FreezeSeizureDeduction> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) {
            wrapper.eq(FreezeSeizureDeduction::getCustomerId, customerId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(FreezeSeizureDeduction::getStatus, status);
        }
        wrapper.orderByDesc(FreezeSeizureDeduction::getCreatedTime);
        IPage<FreezeSeizureDeduction> page = freezeMapper.selectPage(pageQuery.toPage(), wrapper);
        return PageResult.from(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFreezeRecordStatus(Long id, String status, String remark) {
        FreezeSeizureDeduction record = freezeMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "查冻扣记录不存在，id=" + id);
        }
        record.setStatus(status);
        if (StringUtils.hasText(remark)) {
            record.setRemark(remark);
        }
        freezeMapper.updateById(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Alert escalateScreeningResultToAlert(Long resultId, String reason) {
        ScreeningResult result = loadScreeningResult(resultId);
        Alert alert = buildAlert(result, reason);
        AlertRuleDetail detail = buildAlertRuleDetail(result);
        Alert created = alertService.createAlert(alert, List.of(detail));

        result.setReviewStatus("ESCALATED");
        result.setReviewResult("已升级预警");
        result.setReviewReason(reason);
        result.setReviewedBy(SecurityUtils.getCurrentUsername());
        result.setReviewedTime(LocalDateTime.now());
        screeningResultMapper.updateById(result);
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Case createCaseFromScreeningResult(Long resultId, String reason) {
        ScreeningResult result = loadScreeningResult(resultId);
        Alert alert = escalateScreeningResultToAlert(resultId, reason);
        alert.setStatus(AlertStatus.CONFIRMED.getCode());
        alert.setProcessResult("CONFIRMED_SUSPICIOUS");
        alert.setProcessRemark(reason);
        alert.setProcessTime(LocalDateTime.now());
        alertMapper.updateById(alert);

        CaseCreateRequest req = new CaseCreateRequest();
        req.setAlertId(alert.getId());
        req.setCaseType("WATCHLIST_HIT");
        req.setPriority(resolveCasePriority(result.getMatchScore()));
        req.setSummary("名单命中自动建案：" + result.getCustomerName() + " 命中 " + result.getWatchlistName());
        return caseService.createCase(req);
    }

    private int countCustomersByScope(RetrospectiveScreeningJobRequest request) {
        if ("CUSTOMER_IDS".equals(request.getScopeType()) && StringUtils.hasText(request.getCustomerIds())) {
            return (int) java.util.Arrays.stream(request.getCustomerIds().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .count();
        }
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        if ("HIGH_RISK".equals(request.getScopeType())) {
            wrapper.eq(Customer::getRiskLevel, RiskLevel.HIGH.getCode());
        } else if ("ACTIVE_CUSTOMERS".equals(request.getScopeType())) {
            wrapper.eq(Customer::getStatus, "ACTIVE");
        }
        return Math.toIntExact(customerMapper.selectCount(wrapper));
    }

    private Customer loadCustomer(Long customerId) {
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException(ResultCode.CUSTOMER_NOT_FOUND, "客户不存在，id=" + customerId);
        }
        return customer;
    }

    private ScreeningResult loadScreeningResult(Long resultId) {
        ScreeningResult result = screeningResultMapper.selectById(resultId);
        if (result == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "筛查结果不存在，id=" + resultId);
        }
        return result;
    }

    private Alert buildAlert(ScreeningResult result, String reason) {
        Alert alert = new Alert();
        alert.setCustomerId(result.getCustomerId());
        alert.setCustomerName(result.getCustomerName());
        alert.setAlertType("SANCTIONS_HIT");
        alert.setRiskScore(result.getMatchScore() == null ? 90 : result.getMatchScore().intValue());
        alert.setRiskLevel(resolveRiskLevel(result.getMatchScore()));
        alert.setSourceRuleCodes("WATCHLIST_SCREENING");
        alert.setAlertSummary("名单筛查命中：" + result.getCustomerName() + " 命中 " + result.getWatchlistName());
        alert.setProcessRemark(reason);
        alert.setDeduplicateKey("SCREENING:" + result.getId());
        return alert;
    }

    private AlertRuleDetail buildAlertRuleDetail(ScreeningResult result) {
        AlertRuleDetail detail = new AlertRuleDetail();
        detail.setRuleCode("WATCHLIST_SCREENING");
        detail.setRuleName("名单筛查命中");
        detail.setMatchScore(result.getMatchScore());
        detail.setMatchDetail(result.getMatchDetail());
        detail.setCreatedTime(LocalDateTime.now());
        return detail;
    }

    private String resolveRiskLevel(BigDecimal score) {
        if (score == null) {
            return RiskLevel.HIGH.getCode();
        }
        if (score.compareTo(BigDecimal.valueOf(95)) >= 0) {
            return RiskLevel.CRITICAL.getCode();
        }
        return RiskLevel.HIGH.getCode();
    }

    private int resolveCasePriority(BigDecimal score) {
        if (score != null && score.compareTo(BigDecimal.valueOf(95)) >= 0) {
            return 5;
        }
        return 4;
    }
}
