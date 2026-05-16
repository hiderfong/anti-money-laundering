package com.insurance.aml.module.modelmgmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.modelmgmt.mapper.AmlModelLifecycleLogMapper;
import com.insurance.aml.module.modelmgmt.mapper.AmlModelMapper;
import com.insurance.aml.module.modelmgmt.model.dto.ModelCreateRequest;
import com.insurance.aml.module.modelmgmt.model.dto.ModelLifecycleRequest;
import com.insurance.aml.module.modelmgmt.model.dto.ModelOverviewVO;
import com.insurance.aml.module.modelmgmt.model.dto.ModelQueryRequest;
import com.insurance.aml.module.modelmgmt.model.entity.AmlModel;
import com.insurance.aml.module.modelmgmt.model.entity.AmlModelLifecycleLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 反洗钱模型管理服务实现。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AmlModelServiceImpl implements com.insurance.aml.module.modelmgmt.service.AmlModelService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_TEST_PASSED = "TEST_PASSED";
    private static final String STATUS_DEPLOYED = "DEPLOYED";
    private static final String STATUS_MONITORING = "MONITORING";
    private static final String STATUS_ITERATING = "ITERATING";
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    private final AmlModelMapper modelMapper;
    private final AmlModelLifecycleLogMapper lifecycleLogMapper;

    @Override
    public ModelOverviewVO overview() {
        List<AmlModel> models = modelMapper.selectList(new LambdaQueryWrapper<>());
        long total = models.size();
        long draft = countStatus(models, STATUS_DRAFT);
        long testing = countStatus(models, "TESTING") + countStatus(models, STATUS_TEST_PASSED);
        long deployed = countStatus(models, STATUS_DEPLOYED);
        long monitoring = countStatus(models, STATUS_MONITORING);
        long iteration = countStatus(models, STATUS_ITERATING);
        long archived = countStatus(models, STATUS_ARCHIVED);
        long attention = models.stream()
                .filter(model -> "ATTENTION".equals(model.getMonitorStatus())
                        || "DRIFTED".equals(model.getMonitorStatus())
                        || exceeds(model.getFalsePositiveRate(), "0.2000")
                        || exceeds(model.getDriftScore(), "0.1500"))
                .count();

        return ModelOverviewVO.builder()
                .totalModels(total)
                .draftModels(draft)
                .testingModels(testing)
                .deployedModels(deployed)
                .monitoringModels(monitoring)
                .iterationModels(iteration)
                .archivedModels(archived)
                .attentionModels(attention)
                .averageFalsePositiveRate(average(models.stream().map(AmlModel::getFalsePositiveRate).toList()))
                .averageDriftScore(average(models.stream().map(AmlModel::getDriftScore).toList()))
                .build();
    }

    @Override
    public PageResult<AmlModel> pageModels(ModelQueryRequest request) {
        LambdaQueryWrapper<AmlModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(request.getKeyword()), query -> query
                        .like(AmlModel::getModelCode, request.getKeyword())
                        .or()
                        .like(AmlModel::getModelName, request.getKeyword())
                        .or()
                        .like(AmlModel::getOwner, request.getKeyword()))
                .eq(StringUtils.hasText(request.getModelType()), AmlModel::getModelType, request.getModelType())
                .eq(StringUtils.hasText(request.getScenario()), AmlModel::getScenario, request.getScenario())
                .eq(StringUtils.hasText(request.getLifecycleStatus()), AmlModel::getLifecycleStatus, request.getLifecycleStatus())
                .eq(StringUtils.hasText(request.getRiskLevel()), AmlModel::getRiskLevel, request.getRiskLevel())
                .orderByDesc(AmlModel::getUpdatedTime)
                .orderByDesc(AmlModel::getCreatedTime);
        IPage<AmlModel> page = modelMapper.selectPage(request.toPage(), wrapper);
        return PageResult.from(page);
    }

    @Override
    public AmlModel getModel(Long id) {
        return loadModel(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AmlModel createModel(ModelCreateRequest request) {
        ensureModelCodeUnique(request.getModelCode(), null);
        AmlModel model = new AmlModel();
        applyRequest(model, request);
        model.setLifecycleStatus(StringUtils.hasText(request.getLifecycleStatus()) ? request.getLifecycleStatus() : STATUS_DRAFT);
        model.setVersion(StringUtils.hasText(request.getVersion()) ? request.getVersion() : "1.0.0");
        model.setGovernanceLevel(StringUtils.hasText(request.getGovernanceLevel()) ? request.getGovernanceLevel() : "L2");
        model.setRiskLevel(StringUtils.hasText(request.getRiskLevel()) ? request.getRiskLevel() : "MEDIUM");
        model.setMonitorStatus("NOT_STARTED");
        modelMapper.insert(model);
        addLog(model, "CREATE", null, model.getLifecycleStatus(), "创建模型", null, null);
        return model;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AmlModel updateModel(Long id, ModelCreateRequest request) {
        AmlModel model = loadModel(id);
        ensureNotArchived(model);
        ensureModelCodeUnique(request.getModelCode(), id);
        String fromStatus = model.getLifecycleStatus();
        applyRequest(model, request);
        if (!StringUtils.hasText(model.getLifecycleStatus())) {
            model.setLifecycleStatus(fromStatus);
        }
        modelMapper.updateById(model);
        addLog(model, "UPDATE", fromStatus, model.getLifecycleStatus(), "更新模型基础信息", null, null);
        return model;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AmlModel testModel(Long id, ModelLifecycleRequest request) {
        AmlModel model = loadModel(id);
        ensureNotArchived(model);
        String fromStatus = model.getLifecycleStatus();
        model.setLifecycleStatus(STATUS_TEST_PASSED);
        model.setTestResult("PASS");
        model.setLastTestTime(LocalDateTime.now());
        model.setValidationDataset(firstText(request.getTestDataset(), model.getValidationDataset(), "样例验证集"));
        applyMetrics(model, request);
        modelMapper.updateById(model);
        addLog(model, "TEST", fromStatus, STATUS_TEST_PASSED,
                firstText(request.getActionSummary(), "模型测试通过，已记录样例指标"), metricsJson(model), request.getArtifactRef(),
                request.getOperator());
        return model;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AmlModel deployModel(Long id, ModelLifecycleRequest request) {
        AmlModel model = loadModel(id);
        ensureNotArchived(model);
        String fromStatus = model.getLifecycleStatus();
        model.setLifecycleStatus(STATUS_DEPLOYED);
        model.setDeploymentEnv(firstText(request.getDeploymentEnv(), model.getDeploymentEnv(), "UAT"));
        model.setDeployedTime(LocalDateTime.now());
        model.setMonitorStatus("NORMAL");
        modelMapper.updateById(model);
        addLog(model, "DEPLOY", fromStatus, STATUS_DEPLOYED,
                firstText(request.getActionSummary(), "模型已完成部署登记"), metricsJson(model), request.getArtifactRef(),
                request.getOperator());
        return model;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AmlModel monitorModel(Long id, ModelLifecycleRequest request) {
        AmlModel model = loadModel(id);
        ensureNotArchived(model);
        String fromStatus = model.getLifecycleStatus();
        model.setLifecycleStatus(STATUS_MONITORING);
        model.setMonitorStatus(firstText(request.getMonitorStatus(), inferMonitorStatus(request), "NORMAL"));
        model.setLastMonitorTime(LocalDateTime.now());
        applyMetrics(model, request);
        modelMapper.updateById(model);
        addLog(model, "MONITOR", fromStatus, STATUS_MONITORING,
                firstText(request.getActionSummary(), "模型监控指标已刷新"), metricsJson(model), request.getArtifactRef(),
                request.getOperator());
        return model;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AmlModel iterateModel(Long id, ModelLifecycleRequest request) {
        AmlModel model = loadModel(id);
        ensureNotArchived(model);
        String fromStatus = model.getLifecycleStatus();
        model.setLifecycleStatus(STATUS_ITERATING);
        model.setVersion(firstText(request.getTargetVersion(), bumpPatchVersion(model.getVersion())));
        model.setIterationPlan(firstText(request.getIterationPlan(), request.getActionSummary(), model.getIterationPlan()));
        modelMapper.updateById(model);
        addLog(model, "ITERATE", fromStatus, STATUS_ITERATING,
                firstText(request.getActionSummary(), "模型进入迭代优化阶段"), metricsJson(model), request.getArtifactRef(),
                request.getOperator());
        return model;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AmlModel archiveModel(Long id, ModelLifecycleRequest request) {
        AmlModel model = loadModel(id);
        String fromStatus = model.getLifecycleStatus();
        model.setLifecycleStatus(STATUS_ARCHIVED);
        model.setArchiveReason(firstText(request.getArchiveReason(), request.getActionSummary(), "模型下线归档"));
        model.setArchivedTime(LocalDateTime.now());
        model.setMonitorStatus("NOT_STARTED");
        modelMapper.updateById(model);
        addLog(model, "ARCHIVE", fromStatus, STATUS_ARCHIVED,
                model.getArchiveReason(), metricsJson(model), request.getArtifactRef(), request.getOperator());
        return model;
    }

    @Override
    public PageResult<AmlModelLifecycleLog> pageLifecycleLogs(Long modelId, PageQuery pageQuery) {
        loadModel(modelId);
        LambdaQueryWrapper<AmlModelLifecycleLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AmlModelLifecycleLog::getModelId, modelId)
                .orderByDesc(AmlModelLifecycleLog::getActionTime)
                .orderByDesc(AmlModelLifecycleLog::getCreatedTime);
        IPage<AmlModelLifecycleLog> page = lifecycleLogMapper.selectPage(pageQuery.toPage(), wrapper);
        return PageResult.from(page);
    }

    private void applyRequest(AmlModel model, ModelCreateRequest request) {
        BeanUtils.copyProperties(request, model);
        if (!StringUtils.hasText(model.getVersion())) {
            model.setVersion("1.0.0");
        }
        if (!StringUtils.hasText(model.getGovernanceLevel())) {
            model.setGovernanceLevel("L2");
        }
        if (!StringUtils.hasText(model.getRiskLevel())) {
            model.setRiskLevel("MEDIUM");
        }
    }

    private AmlModel loadModel(Long id) {
        AmlModel model = modelMapper.selectById(id);
        if (model == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "模型不存在，id=" + id);
        }
        return model;
    }

    private void ensureNotArchived(AmlModel model) {
        if (STATUS_ARCHIVED.equals(model.getLifecycleStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已归档模型不能继续变更");
        }
    }

    private void ensureModelCodeUnique(String modelCode, Long excludeId) {
        LambdaQueryWrapper<AmlModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AmlModel::getModelCode, modelCode);
        if (excludeId != null) {
            wrapper.ne(AmlModel::getId, excludeId);
        }
        if (modelMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "模型编码已存在：" + modelCode);
        }
    }

    private void applyMetrics(AmlModel model, ModelLifecycleRequest request) {
        model.setPrecisionRate(firstMetric(request.getPrecisionRate(), model.getPrecisionRate(), "0.9000"));
        model.setRecallRate(firstMetric(request.getRecallRate(), model.getRecallRate(), "0.8500"));
        model.setFalsePositiveRate(firstMetric(request.getFalsePositiveRate(), model.getFalsePositiveRate(), "0.1200"));
        model.setDriftScore(firstMetric(request.getDriftScore(), model.getDriftScore(), "0.0500"));
    }

    private void addLog(AmlModel model, String actionType, String fromStatus, String toStatus,
                        String summary, String resultMetric, String artifactRef) {
        addLog(model, actionType, fromStatus, toStatus, summary, resultMetric, artifactRef, null);
    }

    private void addLog(AmlModel model, String actionType, String fromStatus, String toStatus,
                        String summary, String resultMetric, String artifactRef, String operator) {
        AmlModelLifecycleLog log = new AmlModelLifecycleLog();
        log.setModelId(model.getId());
        log.setModelCode(model.getModelCode());
        log.setActionType(actionType);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperator(firstText(operator, SecurityUtils.getCurrentUsername(), "system"));
        log.setActionTime(LocalDateTime.now());
        log.setActionSummary(summary);
        log.setResultMetric(resultMetric);
        log.setArtifactRef(artifactRef);
        lifecycleLogMapper.insert(log);
    }

    private String inferMonitorStatus(ModelLifecycleRequest request) {
        if (exceeds(request.getDriftScore(), "0.1500")) {
            return "DRIFTED";
        }
        if (exceeds(request.getFalsePositiveRate(), "0.2000")) {
            return "ATTENTION";
        }
        return "NORMAL";
    }

    private String metricsJson(AmlModel model) {
        return String.format(
                "{\"precisionRate\":%s,\"recallRate\":%s,\"falsePositiveRate\":%s,\"driftScore\":%s,\"version\":\"%s\"}",
                numberOrNull(model.getPrecisionRate()),
                numberOrNull(model.getRecallRate()),
                numberOrNull(model.getFalsePositiveRate()),
                numberOrNull(model.getDriftScore()),
                model.getVersion());
    }

    private String numberOrNull(BigDecimal value) {
        return value == null ? "null" : value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private long countStatus(List<AmlModel> models, String status) {
        return models.stream().filter(model -> status.equals(model.getLifecycleStatus())).count();
    }

    private boolean exceeds(BigDecimal value, String threshold) {
        return value != null && value.compareTo(new BigDecimal(threshold)) >= 0;
    }

    private BigDecimal average(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal total = present.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(present.size()), 4, RoundingMode.HALF_UP);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal firstMetric(BigDecimal requested, BigDecimal existing, String fallback) {
        if (requested != null) {
            return requested;
        }
        if (existing != null) {
            return existing;
        }
        return new BigDecimal(fallback);
    }

    private String bumpPatchVersion(String version) {
        if (!StringUtils.hasText(version)) {
            return "1.0.1";
        }
        String[] parts = version.split("\\.");
        if (parts.length != 3) {
            return version + ".1";
        }
        try {
            int patch = Integer.parseInt(parts[2]);
            return parts[0] + "." + parts[1] + "." + (patch + 1);
        } catch (NumberFormatException ex) {
            return version + ".1";
        }
    }
}
