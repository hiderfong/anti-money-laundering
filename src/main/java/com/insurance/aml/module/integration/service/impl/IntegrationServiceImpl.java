package com.insurance.aml.module.integration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.integration.adapter.IntegrationAdapter;
import com.insurance.aml.module.integration.adapter.IntegrationAdapterRegistry;
import com.insurance.aml.module.integration.adapter.IntegrationExecutionResult;
import com.insurance.aml.module.integration.mapper.IntegrationConnectorMapper;
import com.insurance.aml.module.integration.mapper.IntegrationJobMapper;
import com.insurance.aml.module.integration.mapper.IntegrationRunMapper;
import com.insurance.aml.module.integration.model.dto.IntegrationConnectorQuery;
import com.insurance.aml.module.integration.model.dto.IntegrationConnectorRequest;
import com.insurance.aml.module.integration.model.dto.IntegrationJobQuery;
import com.insurance.aml.module.integration.model.dto.IntegrationJobRequest;
import com.insurance.aml.module.integration.model.dto.IntegrationOverviewVO;
import com.insurance.aml.module.integration.model.dto.IntegrationRunQuery;
import com.insurance.aml.module.integration.model.entity.IntegrationConnector;
import com.insurance.aml.module.integration.model.entity.IntegrationJob;
import com.insurance.aml.module.integration.model.entity.IntegrationRun;
import com.insurance.aml.module.integration.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 集成中心服务：统一连接器配置、任务调度、运行审计和失败重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationServiceImpl implements IntegrationService {
    private static final String ENABLED = "ENABLED";
    private static final String HEALTHY = "HEALTHY";
    private static final String UNHEALTHY = "UNHEALTHY";
    private static final String RUNNING = "RUNNING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";

    private final IntegrationConnectorMapper connectorMapper;
    private final IntegrationJobMapper jobMapper;
    private final IntegrationRunMapper runMapper;
    private final IntegrationAdapterRegistry adapterRegistry;
    private final IdGenerator idGenerator;

    @Override
    public IntegrationOverviewVO overview() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        long totalConnectors = connectorMapper.selectCount(null);
        long healthyConnectors = countConnectors(HEALTHY);
        long unhealthyConnectors = countConnectors(UNHEALTHY);
        long enabledJobs = jobMapper.selectCount(new LambdaQueryWrapper<IntegrationJob>().eq(IntegrationJob::getEnabled, true));
        long runningJobs = jobMapper.selectCount(new LambdaQueryWrapper<IntegrationJob>().eq(IntegrationJob::getExecutionStatus, RUNNING));
        long failedRuns = runMapper.selectCount(new LambdaQueryWrapper<IntegrationRun>()
                .eq(IntegrationRun::getStatus, FAILED)
                .ge(IntegrationRun::getStartedTime, today));
        List<IntegrationRun> completedToday = runMapper.selectList(new LambdaQueryWrapper<IntegrationRun>()
                .in(IntegrationRun::getStatus, SUCCESS, FAILED)
                .ge(IntegrationRun::getStartedTime, today));
        long successfulRuns = completedToday.stream().filter(run -> SUCCESS.equals(run.getStatus())).count();
        long recordsWritten = completedToday.stream()
                .filter(run -> SUCCESS.equals(run.getStatus()))
                .map(IntegrationRun::getRecordsWritten)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        double successRate = completedToday.isEmpty() ? 0D : successfulRuns * 100D / completedToday.size();
        return IntegrationOverviewVO.builder()
                .totalConnectors(totalConnectors)
                .healthyConnectors(healthyConnectors)
                .unhealthyConnectors(unhealthyConnectors)
                .enabledJobs(enabledJobs)
                .runningJobs(runningJobs)
                .failedRunsToday(failedRuns)
                .recordsWrittenToday(recordsWritten)
                .successRateToday(Math.round(successRate * 10D) / 10D)
                .build();
    }

    @Override
    public PageResult<IntegrationConnector> pageConnectors(IntegrationConnectorQuery query) {
        LambdaQueryWrapper<IntegrationConnector> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(item -> item.like(IntegrationConnector::getConnectorCode, query.getKeyword())
                    .or().like(IntegrationConnector::getConnectorName, query.getKeyword()));
        }
        wrapper.eq(StringUtils.hasText(query.getBusinessType()), IntegrationConnector::getBusinessType, query.getBusinessType())
                .eq(StringUtils.hasText(query.getTransportType()), IntegrationConnector::getTransportType, query.getTransportType())
                .eq(StringUtils.hasText(query.getStatus()), IntegrationConnector::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getHealthStatus()), IntegrationConnector::getHealthStatus, query.getHealthStatus())
                .orderByAsc(IntegrationConnector::getConnectorCode);
        return PageResult.from(connectorMapper.selectPage(query.toPage(), wrapper));
    }

    @Override
    public List<IntegrationConnector> listEnabledConnectors() {
        return connectorMapper.selectList(new LambdaQueryWrapper<IntegrationConnector>()
                .eq(IntegrationConnector::getStatus, ENABLED)
                .orderByAsc(IntegrationConnector::getConnectorName));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationConnector createConnector(IntegrationConnectorRequest request) {
        ensureConnectorCodeUnique(request.getConnectorCode(), null);
        validateConnectorRequest(request);
        IntegrationConnector connector = new IntegrationConnector();
        applyConnector(connector, request);
        connector.setHealthStatus("UNKNOWN");
        connectorMapper.insert(connector);
        return connector;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationConnector updateConnector(Long id, IntegrationConnectorRequest request) {
        IntegrationConnector connector = loadConnector(id);
        ensureConnectorCodeUnique(request.getConnectorCode(), id);
        validateConnectorRequest(request);
        boolean endpointChanged = !Objects.equals(connector.getEndpointUrl(), request.getEndpointUrl())
                || !Objects.equals(connector.getTransportType(), request.getTransportType());
        applyConnector(connector, request);
        if (endpointChanged) {
            connector.setHealthStatus("UNKNOWN");
            connector.setLastErrorMessage(null);
        }
        connectorMapper.updateById(connector);
        return connector;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationRun testConnection(Long connectorId) {
        IntegrationConnector connector = loadConnector(connectorId);
        IntegrationRun run = startRun(null, connector, "HEALTH_CHECK", null,
                "连接器健康检查：" + connector.getConnectorCode());
        LocalDateTime started = run.getStartedTime();
        IntegrationExecutionResult result;
        try {
            result = adapterRegistry.resolve(connector.getTransportType()).healthCheck(connector);
        } catch (Exception exception) {
            result = failedResult(exception);
        }
        completeRun(run, result, 1, started);
        updateConnectorHealth(connector, result);
        return enrichRun(run);
    }

    @Override
    public PageResult<IntegrationJob> pageJobs(IntegrationJobQuery query) {
        LambdaQueryWrapper<IntegrationJob> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(item -> item.like(IntegrationJob::getJobCode, query.getKeyword())
                    .or().like(IntegrationJob::getJobName, query.getKeyword()));
        }
        wrapper.eq(query.getConnectorId() != null, IntegrationJob::getConnectorId, query.getConnectorId())
                .eq(StringUtils.hasText(query.getBusinessObject()), IntegrationJob::getBusinessObject, query.getBusinessObject())
                .eq(query.getEnabled() != null, IntegrationJob::getEnabled, query.getEnabled())
                .eq(StringUtils.hasText(query.getExecutionStatus()), IntegrationJob::getExecutionStatus, query.getExecutionStatus())
                .orderByAsc(IntegrationJob::getJobCode);
        IPage<IntegrationJob> page = jobMapper.selectPage(query.toPage(), wrapper);
        enrichJobs(page.getRecords());
        return PageResult.from(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationJob createJob(IntegrationJobRequest request) {
        ensureJobCodeUnique(request.getJobCode(), null);
        IntegrationConnector connector = loadConnector(request.getConnectorId());
        validateJobRequest(request, connector);
        IntegrationJob job = new IntegrationJob();
        applyJob(job, request);
        job.setExecutionStatus(request.getEnabled() == null || request.getEnabled() ? "IDLE" : "DISABLED");
        job.setNextRunTime(Boolean.FALSE.equals(job.getEnabled()) ? null : nextExecution(job.getCronExpression(), LocalDateTime.now()));
        jobMapper.insert(job);
        job.setConnectorName(connector.getConnectorName());
        return job;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationJob updateJob(Long id, IntegrationJobRequest request) {
        IntegrationJob job = loadJob(id);
        if (RUNNING.equals(job.getExecutionStatus())) {
            throw new BusinessException(ResultCode.INTEGRATION_JOB_RUNNING, "运行中的任务不能修改");
        }
        ensureJobCodeUnique(request.getJobCode(), id);
        IntegrationConnector connector = loadConnector(request.getConnectorId());
        validateJobRequest(request, connector);
        applyJob(job, request);
        job.setExecutionStatus(Boolean.FALSE.equals(job.getEnabled()) ? "DISABLED" : "IDLE");
        job.setNextRunTime(Boolean.FALSE.equals(job.getEnabled()) ? null : nextExecution(job.getCronExpression(), LocalDateTime.now()));
        jobMapper.updateById(job);
        job.setConnectorName(connector.getConnectorName());
        return job;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationRun triggerJob(Long jobId) {
        return executeJob(jobId, "MANUAL", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationRun retryRun(Long runId) {
        IntegrationRun original = loadRun(runId);
        if (!FAILED.equals(original.getStatus()) || original.getJobId() == null) {
            throw new BusinessException(ResultCode.INTEGRATION_RUN_NOT_RETRYABLE, "仅失败的任务运行记录允许重试");
        }
        return executeJob(original.getJobId(), "RETRY", original.getId());
    }

    @Override
    public PageResult<IntegrationRun> pageRuns(IntegrationRunQuery query) {
        LambdaQueryWrapper<IntegrationRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getJobId() != null, IntegrationRun::getJobId, query.getJobId())
                .eq(query.getConnectorId() != null, IntegrationRun::getConnectorId, query.getConnectorId())
                .eq(StringUtils.hasText(query.getStatus()), IntegrationRun::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getTriggerType()), IntegrationRun::getTriggerType, query.getTriggerType())
                .orderByDesc(IntegrationRun::getStartedTime);
        IPage<IntegrationRun> page = runMapper.selectPage(query.toPage(), wrapper);
        enrichRuns(page.getRecords());
        return PageResult.from(page);
    }

    @Override
    public IntegrationRun getRun(Long runId) {
        return enrichRun(loadRun(runId));
    }

    @Override
    public void runDueJobs() {
        List<IntegrationJob> jobs = jobMapper.selectList(new LambdaQueryWrapper<IntegrationJob>()
                .eq(IntegrationJob::getEnabled, true)
                .ne(IntegrationJob::getExecutionStatus, RUNNING)
                .le(IntegrationJob::getNextRunTime, LocalDateTime.now())
                .orderByAsc(IntegrationJob::getNextRunTime)
                .last("LIMIT 20"));
        for (IntegrationJob job : jobs) {
            try {
                executeJob(job.getId(), "SCHEDULED", null);
            } catch (Exception exception) {
                log.error("定时集成任务执行异常，jobCode={}, error={}", job.getJobCode(), exception.getMessage(), exception);
            }
        }
    }

    private IntegrationRun executeJob(Long jobId, String triggerType, Long retryOfRunId) {
        IntegrationJob job = loadJob(jobId);
        if (Boolean.FALSE.equals(job.getEnabled())) {
            throw new BusinessException(ResultCode.INTEGRATION_JOB_DISABLED, "任务已停用：" + job.getJobCode());
        }
        if (RUNNING.equals(job.getExecutionStatus())) {
            throw new BusinessException(ResultCode.INTEGRATION_JOB_RUNNING, "任务正在运行：" + job.getJobCode());
        }
        IntegrationConnector connector = loadConnector(job.getConnectorId());
        if (!ENABLED.equals(connector.getStatus())) {
            throw new BusinessException(ResultCode.INTEGRATION_CONNECTOR_DISABLED, "连接器已停用：" + connector.getConnectorCode());
        }

        job.setExecutionStatus(RUNNING);
        job.setLastRunTime(LocalDateTime.now());
        jobMapper.updateById(job);
        IntegrationRun run = startRun(job, connector, triggerType, retryOfRunId,
                "业务对象=" + job.getBusinessObject() + "，方向=" + job.getDirection() + "，批量=" + job.getBatchSize());
        LocalDateTime started = run.getStartedTime();
        int maxAttempts = 1 + Math.max(0, job.getMaxRetries() == null ? connector.getMaxRetries() : job.getMaxRetries());
        IntegrationExecutionResult result = null;
        int attempt = 0;
        try {
            IntegrationAdapter adapter = adapterRegistry.resolve(connector.getTransportType());
            while (attempt < maxAttempts) {
                attempt++;
                try {
                    result = adapter.execute(connector, job, attempt);
                } catch (Exception exception) {
                    result = failedResult(exception);
                }
                if (result.isSuccess()) {
                    break;
                }
            }
        } catch (Exception exception) {
            attempt = Math.max(1, attempt);
            result = failedResult(exception);
        } finally {
            job.setNextRunTime(nextExecution(job.getCronExpression(), LocalDateTime.now()));
        }
        completeRun(run, Objects.requireNonNull(result), attempt, started);
        updateConnectorHealth(connector, result);
        job.setExecutionStatus(result.isSuccess() ? SUCCESS : FAILED);
        jobMapper.updateById(job);
        return enrichRun(run);
    }

    private IntegrationRun startRun(IntegrationJob job, IntegrationConnector connector, String triggerType,
                                    Long retryOfRunId, String requestSummary) {
        IntegrationRun run = new IntegrationRun();
        run.setRunNo(idGenerator.generate("INT"));
        run.setJobId(job == null ? null : job.getId());
        run.setConnectorId(connector.getId());
        run.setRetryOfRunId(retryOfRunId);
        run.setTriggerType(triggerType);
        run.setStatus(RUNNING);
        run.setAttemptCount(0);
        run.setRetryCount(0);
        run.setRecordsRead(0);
        run.setRecordsWritten(0);
        run.setRecordsSkipped(0);
        run.setErrorCount(0);
        run.setStartedTime(LocalDateTime.now());
        run.setRequestSummary(requestSummary);
        run.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        run.setExecutedBy(currentOperator(triggerType));
        runMapper.insert(run);
        return run;
    }

    private void completeRun(IntegrationRun run, IntegrationExecutionResult result, int attempt, LocalDateTime started) {
        LocalDateTime completed = LocalDateTime.now();
        run.setStatus(result.isSuccess() ? SUCCESS : FAILED);
        run.setAttemptCount(attempt);
        run.setRetryCount(Math.max(0, attempt - 1));
        run.setRecordsRead(result.getRecordsRead());
        run.setRecordsWritten(result.getRecordsWritten());
        run.setRecordsSkipped(result.getRecordsSkipped());
        run.setErrorCount(result.getErrorCount());
        run.setResponseSummary(truncate(result.getResponseSummary(), 1024));
        run.setErrorMessage(truncate(result.getErrorMessage(), 1024));
        run.setCompletedTime(completed);
        run.setDurationMs(Math.max(0, Duration.between(started, completed).toMillis()));
        runMapper.updateById(run);
    }

    private void updateConnectorHealth(IntegrationConnector connector, IntegrationExecutionResult result) {
        LocalDateTime now = LocalDateTime.now();
        connector.setLastHealthCheckTime(now);
        if (result.isSuccess()) {
            connector.setHealthStatus(HEALTHY);
            connector.setLastSuccessTime(now);
            connector.setLastErrorMessage(null);
        } else {
            connector.setHealthStatus(UNHEALTHY);
            connector.setLastFailureTime(now);
            connector.setLastErrorMessage(truncate(result.getErrorMessage(), 1024));
        }
        connectorMapper.updateById(connector);
    }

    private void validateConnectorRequest(IntegrationConnectorRequest request) {
        adapterRegistry.resolve(request.getTransportType());
        if (!"NONE".equals(request.getAuthType()) && !StringUtils.hasText(request.getCredentialRef())) {
            throw new BusinessException(ResultCode.INTEGRATION_CONFIG_INVALID, "启用认证时必须填写凭据引用");
        }
        if ("NONE".equals(request.getAuthType())) {
            request.setCredentialRef(null);
        }
    }

    private void validateJobRequest(IntegrationJobRequest request, IntegrationConnector connector) {
        nextExecution(request.getCronExpression(), LocalDateTime.now());
        if (Boolean.TRUE.equals(request.getEnabled()) && !ENABLED.equals(connector.getStatus())) {
            throw new BusinessException(ResultCode.INTEGRATION_CONNECTOR_DISABLED, "启用任务前必须先启用连接器");
        }
    }

    private void applyConnector(IntegrationConnector connector, IntegrationConnectorRequest request) {
        connector.setConnectorCode(request.getConnectorCode());
        connector.setConnectorName(request.getConnectorName());
        connector.setBusinessType(request.getBusinessType());
        connector.setTransportType(request.getTransportType());
        connector.setEndpointUrl(request.getEndpointUrl());
        connector.setAuthType(request.getAuthType());
        connector.setCredentialRef(request.getCredentialRef());
        connector.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : ENABLED);
        connector.setTimeoutSeconds(request.getTimeoutSeconds() == null ? 30 : request.getTimeoutSeconds());
        connector.setMaxRetries(request.getMaxRetries() == null ? 2 : request.getMaxRetries());
        connector.setRetryIntervalSeconds(request.getRetryIntervalSeconds() == null ? 30 : request.getRetryIntervalSeconds());
        connector.setDescription(request.getDescription());
    }

    private void applyJob(IntegrationJob job, IntegrationJobRequest request) {
        job.setJobCode(request.getJobCode());
        job.setJobName(request.getJobName());
        job.setConnectorId(request.getConnectorId());
        job.setBusinessObject(request.getBusinessObject());
        job.setDirection(request.getDirection());
        job.setCronExpression(request.getCronExpression());
        job.setBatchSize(request.getBatchSize() == null ? 1000 : request.getBatchSize());
        job.setMaxRetries(request.getMaxRetries() == null ? 2 : request.getMaxRetries());
        job.setEnabled(request.getEnabled() == null || request.getEnabled());
        job.setDescription(request.getDescription());
    }

    private void ensureConnectorCodeUnique(String code, Long excludedId) {
        LambdaQueryWrapper<IntegrationConnector> wrapper = new LambdaQueryWrapper<IntegrationConnector>()
                .eq(IntegrationConnector::getConnectorCode, code)
                .ne(excludedId != null, IntegrationConnector::getId, excludedId);
        if (connectorMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.INTEGRATION_CONNECTOR_CODE_EXISTS, code);
        }
    }

    private void ensureJobCodeUnique(String code, Long excludedId) {
        LambdaQueryWrapper<IntegrationJob> wrapper = new LambdaQueryWrapper<IntegrationJob>()
                .eq(IntegrationJob::getJobCode, code)
                .ne(excludedId != null, IntegrationJob::getId, excludedId);
        if (jobMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.INTEGRATION_JOB_CODE_EXISTS, code);
        }
    }

    private IntegrationConnector loadConnector(Long id) {
        IntegrationConnector connector = connectorMapper.selectById(id);
        if (connector == null) {
            throw new BusinessException(ResultCode.INTEGRATION_CONNECTOR_NOT_FOUND, String.valueOf(id));
        }
        return connector;
    }

    private IntegrationJob loadJob(Long id) {
        IntegrationJob job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.INTEGRATION_JOB_NOT_FOUND, String.valueOf(id));
        }
        return job;
    }

    private IntegrationRun loadRun(Long id) {
        IntegrationRun run = runMapper.selectById(id);
        if (run == null) {
            throw new BusinessException(ResultCode.INTEGRATION_RUN_NOT_FOUND, String.valueOf(id));
        }
        return run;
    }

    private LocalDateTime nextExecution(String cron, LocalDateTime from) {
        try {
            LocalDateTime next = CronExpression.parse(cron).next(from);
            if (next == null) {
                throw new IllegalArgumentException("Cron表达式没有下一次执行时间");
            }
            return next;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ResultCode.INTEGRATION_CONFIG_INVALID, "Cron表达式无效：" + cron);
        }
    }

    private long countConnectors(String healthStatus) {
        return connectorMapper.selectCount(new LambdaQueryWrapper<IntegrationConnector>()
                .eq(IntegrationConnector::getHealthStatus, healthStatus));
    }

    private String currentOperator(String triggerType) {
        String username = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(username) ? username : "SCHEDULED".equals(triggerType) ? "scheduler" : "system";
    }

    private IntegrationExecutionResult failedResult(Exception exception) {
        String message = StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName();
        return IntegrationExecutionResult.builder().success(false).errorCount(1).errorMessage(message).build();
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void enrichJobs(List<IntegrationJob> jobs) {
        Map<Long, String> connectorNames = new HashMap<>();
        for (IntegrationJob job : jobs) {
            job.setConnectorName(connectorNames.computeIfAbsent(job.getConnectorId(), id -> {
                IntegrationConnector connector = connectorMapper.selectById(id);
                return connector == null ? "已删除连接器" : connector.getConnectorName();
            }));
        }
    }

    private void enrichRuns(List<IntegrationRun> runs) {
        Map<Long, String> connectorNames = new HashMap<>();
        Map<Long, String> jobNames = new HashMap<>();
        for (IntegrationRun run : runs) {
            run.setConnectorName(connectorNames.computeIfAbsent(run.getConnectorId(), id -> {
                IntegrationConnector connector = connectorMapper.selectById(id);
                return connector == null ? "已删除连接器" : connector.getConnectorName();
            }));
            if (run.getJobId() != null) {
                run.setJobName(jobNames.computeIfAbsent(run.getJobId(), id -> {
                    IntegrationJob job = jobMapper.selectById(id);
                    return job == null ? "已删除任务" : job.getJobName();
                }));
            } else {
                run.setJobName("连接检查");
            }
        }
    }

    private IntegrationRun enrichRun(IntegrationRun run) {
        enrichRuns(List.of(run));
        return run;
    }
}
