package com.insurance.aml.module.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.enums.AlertProcessResult;
import com.insurance.aml.common.enums.AlertStatus;
import com.insurance.aml.common.enums.RiskLevel;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.alert.mapper.AlertAssignmentLogMapper;
import com.insurance.aml.module.alert.mapper.AlertMapper;
import com.insurance.aml.module.alert.mapper.AlertRuleDetailMapper;
import com.insurance.aml.module.alert.model.dto.*;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.alert.model.entity.AlertAssignmentLog;
import com.insurance.aml.module.alert.model.entity.AlertRuleDetail;
import com.insurance.aml.module.alert.controller.AlertController;
import com.insurance.aml.module.alert.service.AlertService;
import com.insurance.aml.module.casemgmt.mapper.CaseMapper;
import com.insurance.aml.module.casemgmt.mapper.StrReportMapper;
import com.insurance.aml.module.casemgmt.model.dto.CaseCreateRequest;
import com.insurance.aml.module.casemgmt.model.entity.Case;
import com.insurance.aml.module.casemgmt.model.entity.StrReport;
import com.insurance.aml.module.casemgmt.service.CaseService;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 预警管理服务实现类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AlertServiceImpl implements AlertService {

    private final AlertMapper alertMapper;
    private final AlertRuleDetailMapper alertRuleDetailMapper;
    private final AlertAssignmentLogMapper assignmentLogMapper;
    private final IdGenerator idGenerator;
    private final CaseService caseService;
    private final CaseMapper caseMapper;
    private final StrReportMapper strReportMapper;
    private final TransactionMapper transactionMapper;
    private final Executor amlTaskExecutor;
    private final SysUserMapper sysUserMapper;

    /**
     * 创建预警
     * 生成预警编号，保存预警及规则明细，尝试自动分配
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Alert createAlert(Alert alert, List<AlertRuleDetail> ruleDetails) {
        log.info("创建预警，客户ID：{}，预警类型：{}，风险等级：{}",
                alert.getCustomerId(), alert.getAlertType(), alert.getRiskLevel());

        // 生成预警编号
        alert.setAlertNo(idGenerator.generateAlertNo());

        // 设置初始状态为NEW
        alert.setStatus(AlertStatus.NEW.getCode());

        // 设置时间
        LocalDateTime now = LocalDateTime.now();
        alert.setCreatedTime(now);
        alert.setUpdatedTime(now);

        // 保存预警
        alertMapper.insert(alert);

        // 保存规则明细
        if (!CollectionUtils.isEmpty(ruleDetails)) {
            for (AlertRuleDetail detail : ruleDetails) {
                detail.setAlertId(alert.getId());
                detail.setCreatedTime(now);
                alertRuleDetailMapper.insert(detail);
            }
        }

        log.info("预警创建成功，预警ID：{}，预警编号：{}", alert.getId(), alert.getAlertNo());

        // 尝试自动分配
        tryAutoAssignSingleAlert(alert);

        return alert;
    }

    /**
     * 异步创建预警 - 不阻塞调用方
     *
     * 通过CompletableFuture异步执行预警入库、规则明细保存、自动分配。
     * 使用注入的amlTaskExecutor避免Spring AOP自调用失效问题。
     *
     * @param alert 预警信息
     * @param ruleDetails 命中规则明细
     * @return CompletableFuture，包含创建的预警
     */
    @Override
    public CompletableFuture<Alert> createAlertAsync(Alert alert, List<AlertRuleDetail> ruleDetails) {
        log.info("[异步预警] 开始创建预警: customerId={}, alertType={}, riskLevel={}",
                alert.getCustomerId(), alert.getAlertType(), alert.getRiskLevel());

        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        Alert created = createAlert(alert, ruleDetails);
                        log.info("[异步预警] 预警创建完成: alertId={}, alertNo={}",
                                created.getId(), created.getAlertNo());
                        return created;
                    } catch (Exception e) {
                        log.error("[异步预警] 预警创建失败: customerId={}, alertType={}, error={}",
                                alert.getCustomerId(), alert.getAlertType(), e.getMessage(), e);
                        throw new RuntimeException("异步创建预警失败", e);
                    }
                },
                amlTaskExecutor
        );
    }

    /**
     * 分页查询预警
     * 支持按预警类型、风险等级、状态、处理人、客户、时间范围过滤
     */
    @Override
    public PageResult<AlertVO> pageQueryAlerts(AlertQueryRequest req) {
        log.debug("分页查询预警，条件：alertType={}, riskLevel={}, status={}, assignedTo={}, customerId={}",
                req.getAlertType(), req.getRiskLevel(), req.getStatus(), req.getAssignedTo(), req.getCustomerId());

        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();

        // 预警类型过滤
        if (StringUtils.hasText(req.getAlertType())) {
            wrapper.eq(Alert::getAlertType, req.getAlertType());
        }
        // 风险等级过滤
        if (StringUtils.hasText(req.getRiskLevel())) {
            wrapper.eq(Alert::getRiskLevel, req.getRiskLevel());
        }
        // 状态过滤
        if (StringUtils.hasText(req.getStatus())) {
            wrapper.eq(Alert::getStatus, req.getStatus());
        }
        // 处理人过滤
        if (req.getAssignedTo() != null) {
            wrapper.eq(Alert::getAssignedTo, req.getAssignedTo());
        }
        // 客户ID过滤
        if (req.getCustomerId() != null) {
            wrapper.eq(Alert::getCustomerId, req.getCustomerId());
        }
        // 时间范围过滤
        if (req.getStartTime() != null) {
            wrapper.ge(Alert::getCreatedTime, req.getStartTime());
        }
        if (req.getEndTime() != null) {
            wrapper.le(Alert::getCreatedTime, req.getEndTime());
        }

        // 按创建时间倒序
        wrapper.orderByDesc(Alert::getCreatedTime);

        // 执行分页查询
        IPage<Alert> page = req.toPage();
        IPage<Alert> result = alertMapper.selectPage(page, wrapper);

        List<Alert> alerts = result.getRecords();

        // 批量查询关联的规则明细（消除N+1）
        Map<Long, List<AlertRuleDetail>> detailMap = Collections.emptyMap();
        if (!alerts.isEmpty()) {
            List<Long> alertIds = alerts.stream().map(Alert::getId).collect(Collectors.toList());
            List<AlertRuleDetail> allDetails = alertRuleDetailMapper.selectList(
                    new LambdaQueryWrapper<AlertRuleDetail>()
                            .in(AlertRuleDetail::getAlertId, alertIds)
            );
            detailMap = allDetails.stream()
                    .collect(Collectors.groupingBy(AlertRuleDetail::getAlertId));
        }

        // 转换为VO并组装规则明细
        final Map<Long, List<AlertRuleDetail>> finalDetailMap = detailMap;
        IPage<AlertVO> voPage = result.convert(alert -> {
            AlertVO vo = new AlertVO();
            BeanUtils.copyProperties(alert, vo);
            vo.setRuleDetails(finalDetailMap.getOrDefault(alert.getId(), Collections.emptyList()));
            return vo;
        });

        return PageResult.from(voPage);
    }

    /**
     * 获取预警详情
     * 加载预警信息及关联的规则明细
     */
    @Override
    public AlertVO getAlertDetail(Long id) {
        log.debug("获取预警详情，预警ID：{}", id);

        Alert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new BusinessException(ResultCode.ALERT_NOT_FOUND, "预警不存在，ID：" + id);
        }

        AlertVO vo = new AlertVO();
        BeanUtils.copyProperties(alert, vo);

        // 加载规则明细
        LambdaQueryWrapper<AlertRuleDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(AlertRuleDetail::getAlertId, id);
        List<AlertRuleDetail> details = alertRuleDetailMapper.selectList(detailWrapper);
        vo.setRuleDetails(details);

        return vo;
    }

    /**
     * 获取预警处置链路。
     *
     * 聚合预警、命中规则、关联交易、案件与STR报告，供前端详情弹窗展示真实处置闭环。
     */
    @Override
    public AlertDispositionChainVO getDispositionChain(Long id) {
        log.debug("获取预警处置链路，预警ID：{}", id);

        Alert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new BusinessException(ResultCode.ALERT_NOT_FOUND, "预警不存在，ID：" + id);
        }

        List<AlertRuleDetail> ruleDetails = alertRuleDetailMapper.selectList(
                new LambdaQueryWrapper<AlertRuleDetail>()
                        .eq(AlertRuleDetail::getAlertId, id)
        );
        List<Transaction> transactions = findRelatedTransactions(alert.getRelatedTransactionIds());
        List<Case> cases = caseMapper.selectList(
                new LambdaQueryWrapper<Case>()
                        .eq(Case::getAlertId, id)
                        .orderByDesc(Case::getCreatedTime)
        );
        List<StrReport> strReports = findStrReports(cases);

        AlertDispositionChainVO vo = new AlertDispositionChainVO();
        vo.setAlertId(alert.getId());
        vo.setAlertNo(alert.getAlertNo());
        vo.setCustomerId(alert.getCustomerId());
        vo.setCustomerName(alert.getCustomerName());
        vo.setAlertStatus(alert.getStatus());
        vo.setProcessResult(alert.getProcessResult());
        vo.setTransactions(transactions.stream().map(this::toTransactionNode).collect(Collectors.toList()));
        vo.setCases(cases.stream().map(this::toCaseNode).collect(Collectors.toList()));
        vo.setStrReports(strReports.stream().map(this::toStrReportNode).collect(Collectors.toList()));

        AlertDispositionChainVO.Summary summary = vo.getSummary();
        summary.setTransactionCount(transactions.size());
        summary.setCaseCount(cases.size());
        summary.setStrReportCount(strReports.size());
        summary.setHasCase(!cases.isEmpty());
        summary.setHasStrReport(!strReports.isEmpty());
        summary.setSubmittedToRegulator(strReports.stream().anyMatch(this::isSubmittedToRegulator));

        vo.setSteps(buildDispositionSteps(alert, ruleDetails, transactions, cases, strReports));
        return vo;
    }

    /**
     * 分配预警
     * 校验预警状态（必须为NEW或ASSIGNED），更新分配信息，记录分配日志
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignAlert(AlertAssignRequest req) {
        log.info("分配预警，预警ID：{}，分配给：{}", req.getAlertId(), req.getAssignTo());

        Alert alert = alertMapper.selectById(req.getAlertId());
        if (alert == null) {
            throw new BusinessException(ResultCode.ALERT_NOT_FOUND, "预警不存在，ID：" + req.getAlertId());
        }

        // 校验状态：只有NEW或ASSIGNED状态才能分配
        if (!AlertStatus.NEW.getCode().equals(alert.getStatus()) && !AlertStatus.ASSIGNED.getCode().equals(alert.getStatus())) {
            throw new BusinessException(ResultCode.ALERT_STATUS_ERROR,
                    "当前预警状态不允许分配，状态：" + alert.getStatus());
        }

        // 记录原处理人
        Long fromUserId = alert.getAssignedTo();

        // 更新预警分配信息
        alert.setAssignedTo(req.getAssignTo());
        alert.setAssignedTime(LocalDateTime.now());
        alert.setStatus(AlertStatus.ASSIGNED.getCode());
        alert.setUpdatedTime(LocalDateTime.now());
        alertMapper.updateById(alert);

        // 保存分配日志
        saveAssignmentLog(alert.getId(), fromUserId, req.getAssignTo(),
                "MANUAL", req.getAssignReason(), String.valueOf(SecurityUtils.getCurrentUserId()));

        log.info("预警分配成功，预警ID：{}，分配给：{}", req.getAlertId(), req.getAssignTo());
    }

    /**
     * 处理预警
     * 校验状态（必须为ASSIGNED或PROCESSING），更新处理结果
     * CONFIRMED_SUSPICIOUS -> 状态变为CONFIRMED并触发案件创建
     * EXCLUDED -> 状态变为EXCLUDED
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processAlert(AlertProcessRequest req) {
        log.info("处理预警，预警ID：{}，处理结果：{}", req.getAlertId(), req.getProcessResult());

        Alert alert = alertMapper.selectById(req.getAlertId());
        if (alert == null) {
            throw new BusinessException(ResultCode.ALERT_NOT_FOUND, "预警不存在，ID：" + req.getAlertId());
        }

        // 校验状态：只有ASSIGNED或PROCESSING状态才能处理
        if (!AlertStatus.ASSIGNED.getCode().equals(alert.getStatus()) && !AlertStatus.PROCESSING.getCode().equals(alert.getStatus())) {
            throw new BusinessException(ResultCode.ALERT_STATUS_ERROR,
                    "当前预警状态不允许处理，状态：" + alert.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();

        // 设置处理信息
        alert.setProcessResult(req.getProcessResult());
        alert.setProcessRemark(req.getProcessRemark());
        alert.setProcessTime(now);
        alert.setUpdatedTime(now);

        boolean shouldCreateCase = false;

        // 根据处理结果设置状态
        String processResult = req.getProcessResult();
        if (AlertProcessResult.CONFIRMED_SUSPICIOUS.getCode().equals(processResult)) {
            alert.setStatus(AlertStatus.CONFIRMED.getCode());
            shouldCreateCase = true;
            log.info("预警已确认可疑，预警ID：{}，准备创建案件", req.getAlertId());
        } else if (AlertProcessResult.EXCLUDED.getCode().equals(processResult)) {
            alert.setStatus(AlertStatus.EXCLUDED.getCode());
            log.info("预警已排除，预警ID：{}", req.getAlertId());
        } else if (AlertProcessResult.ESCALATED.getCode().equals(processResult)) {
            alert.setStatus(AlertStatus.ESCALATED.getCode());
            log.info("预警已升级，预警ID：{}", req.getAlertId());
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的处理结果：" + processResult);
        }

        alertMapper.updateById(alert);
        if (shouldCreateCase) {
            caseService.createCase(buildCaseCreateRequest(alert));
            log.info("预警确认后已创建案件，预警ID：{}", req.getAlertId());
        }
        log.info("预警处理完成，预警ID：{}，新状态：{}", req.getAlertId(), alert.getStatus());
    }

    /**
     * 批量处理预警
     * 遍历预警ID列表，逐一调用processAlert处理
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchProcess(List<Long> alertIds, String action) {
        log.info("批量处理预警，数量：{}，处理动作：{}", alertIds.size(), action);

        for (Long alertId : alertIds) {
            AlertProcessRequest processReq = new AlertProcessRequest();
            processReq.setAlertId(alertId);
            processReq.setProcessResult(action);
            processReq.setProcessRemark("批量处理");
            processAlert(processReq);
        }

        log.info("批量处理完成，共处理 {} 条预警", alertIds.size());
    }

    /**
     * 自动分配预警
     * 查询状态为NEW的预警，轮询分配给可用的AML专员
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoAssignAlerts() {
        log.info("开始自动分配预警...");

        // 查询所有NEW状态的预警
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Alert::getStatus, AlertStatus.NEW.getCode());
        wrapper.orderByAsc(Alert::getCreatedTime);
        List<Alert> newAlerts = alertMapper.selectList(wrapper);

        if (CollectionUtils.isEmpty(newAlerts)) {
            log.info("没有待分配的预警");
            return;
        }

        log.info("待分配预警数量：{}", newAlerts.size());

        List<Long> amlOfficerIds = getAvailableAmlOfficers();

        if (CollectionUtils.isEmpty(amlOfficerIds)) {
            log.warn("没有可用的AML专员，跳过自动分配");
            return;
        }

        // 轮询分配
        AtomicInteger index = new AtomicInteger(0);
        for (Alert alert : newAlerts) {
            Long officerId = amlOfficerIds.get(index.getAndIncrement() % amlOfficerIds.size());
            doAssignAlert(alert, officerId, "AUTO", "系统自动分配");
        }

        log.info("自动分配完成，共分配 {} 条预警给 {} 名AML专员", newAlerts.size(), amlOfficerIds.size());
    }

    /**
     * 升级超期预警
     * 查询分配超过48小时且状态仍为ASSIGNED的预警，自动升级
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void escalateOverdueAlerts() {
        log.info("开始检查超期预警...");

        // 计算48小时前的时间点
        LocalDateTime threshold = LocalDateTime.now().minus(48, ChronoUnit.HOURS);

        // 查询分配时间早于48小时前且状态仍为ASSIGNED的预警
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Alert::getStatus, AlertStatus.ASSIGNED.getCode());
        wrapper.le(Alert::getAssignedTime, threshold);
        List<Alert> overdueAlerts = alertMapper.selectList(wrapper);

        if (CollectionUtils.isEmpty(overdueAlerts)) {
            log.info("没有超期预警");
            return;
        }

        log.info("超期预警数量：{}", overdueAlerts.size());

        Long managerId = getEscalationManager();

        for (Alert alert : overdueAlerts) {
            Long fromUserId = alert.getAssignedTo();

            // 更新预警状态为ESCALATED
            alert.setStatus(AlertStatus.ESCALATED.getCode());
            alert.setUpdatedTime(LocalDateTime.now());
            alertMapper.updateById(alert);

            // 记录升级分配日志
            saveAssignmentLog(alert.getId(), fromUserId, managerId,
                    "ESCALATION", "预警处理超时（超过48小时），自动升级", "SYSTEM");

            log.info("预警已升级，预警ID：{}，原处理人：{}，升级给：{}", alert.getId(), fromUserId, managerId);
        }

        log.info("超期预警升级完成，共升级 {} 条预警", overdueAlerts.size());
    }

    // ==================== 统计方法 ====================

    /**
     * 获取预警统计信息
     * 按状态和风险等级分别统计数量
     */
    @Override
    public AlertController.AlertStatisticsVO getAlertStatistics() {
        log.debug("获取预警统计信息");

        AlertController.AlertStatisticsVO statistics = new AlertController.AlertStatisticsVO();

        // 统计各状态数量
        Map<String, Long> statusCount = new HashMap<>();
        String[] statuses = {
                AlertStatus.NEW.getCode(), AlertStatus.ASSIGNED.getCode(), AlertStatus.PROCESSING.getCode(),
                AlertStatus.CONFIRMED.getCode(), AlertStatus.EXCLUDED.getCode(), AlertStatus.ESCALATED.getCode()
        };
        for (String status : statuses) {
            LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Alert::getStatus, status);
            Long count = alertMapper.selectCount(wrapper);
            statusCount.put(status, count);
        }
        statistics.setCountByStatus(statusCount);

        // 统计各风险等级数量
        Map<String, Long> riskLevelCount = new HashMap<>();
        String[] riskLevels = {
                RiskLevel.LOW.getCode(), RiskLevel.MEDIUM.getCode(),
                RiskLevel.HIGH.getCode(), RiskLevel.CRITICAL.getCode()
        };
        for (String level : riskLevels) {
            LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Alert::getRiskLevel, level);
            Long count = alertMapper.selectCount(wrapper);
            riskLevelCount.put(level, count);
        }
        statistics.setCountByRiskLevel(riskLevelCount);

        // 总数
        statistics.setTotalCount(alertMapper.selectCount(null));

        return statistics;
    }

    // ==================== 私有辅助方法 ====================

    private List<Transaction> findRelatedTransactions(String relatedTransactionIds) {
        List<Long> transactionIds = parseLongIds(relatedTransactionIds);
        if (CollectionUtils.isEmpty(transactionIds)) {
            return Collections.emptyList();
        }
        List<Transaction> transactions = transactionMapper.selectBatchIds(transactionIds);
        transactions.sort(Comparator.comparing(
                Transaction::getTransactionTime,
                Comparator.nullsLast(Comparator.naturalOrder())
        ).reversed());
        return transactions;
    }

    private List<StrReport> findStrReports(List<Case> cases) {
        if (CollectionUtils.isEmpty(cases)) {
            return Collections.emptyList();
        }
        List<Long> caseIds = cases.stream().map(Case::getId).collect(Collectors.toList());
        return strReportMapper.selectList(
                new LambdaQueryWrapper<StrReport>()
                        .in(StrReport::getCaseId, caseIds)
                        .orderByDesc(StrReport::getCreatedTime)
        );
    }

    private List<Long> parseLongIds(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (String token : raw.split("[^0-9]+")) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            try {
                Long id = Long.valueOf(token);
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                log.debug("忽略无法解析的关联交易ID片段：{}", token);
            }
        }
        return ids;
    }

    private AlertDispositionChainVO.TransactionNode toTransactionNode(Transaction transaction) {
        AlertDispositionChainVO.TransactionNode node = new AlertDispositionChainVO.TransactionNode();
        node.setId(transaction.getId());
        node.setTransactionNo(transaction.getTransactionNo());
        node.setPolicyId(transaction.getPolicyId());
        node.setTransactionType(transaction.getTransactionType());
        node.setAmount(transaction.getAmount());
        node.setCurrency(transaction.getCurrency());
        node.setPaymentMethod(transaction.getPaymentMethod());
        node.setStatus(transaction.getStatus());
        node.setCounterpartyName(transaction.getCounterpartyName());
        node.setTransactionTime(transaction.getTransactionTime());
        return node;
    }

    private AlertDispositionChainVO.CaseNode toCaseNode(Case caseEntity) {
        AlertDispositionChainVO.CaseNode node = new AlertDispositionChainVO.CaseNode();
        node.setId(caseEntity.getId());
        node.setCaseNo(caseEntity.getCaseNo());
        node.setCaseStatus(caseEntity.getCaseStatus());
        node.setCaseType(caseEntity.getCaseType());
        node.setPriority(caseEntity.getPriority());
        node.setSummary(caseEntity.getSummary());
        node.setCreatedTime(caseEntity.getCreatedTime());
        node.setSubmitTime(caseEntity.getSubmitTime());
        node.setCloseTime(caseEntity.getCloseTime());
        return node;
    }

    private AlertDispositionChainVO.StrReportNode toStrReportNode(StrReport report) {
        AlertDispositionChainVO.StrReportNode node = new AlertDispositionChainVO.StrReportNode();
        node.setId(report.getId());
        node.setCaseId(report.getCaseId());
        node.setReportNo(report.getReportNo());
        node.setReportType(report.getReportType());
        node.setReportStatus(report.getReportStatus());
        node.setSubmitResult(report.getSubmitResult());
        node.setCreatedTime(report.getCreatedTime());
        node.setSubmitTime(report.getSubmitTime());
        return node;
    }

    private List<AlertDispositionChainVO.Step> buildDispositionSteps(
            Alert alert,
            List<AlertRuleDetail> ruleDetails,
            List<Transaction> transactions,
            List<Case> cases,
            List<StrReport> strReports
    ) {
        List<AlertDispositionChainVO.Step> steps = new ArrayList<>();
        LocalDateTime firstTransactionTime = transactions.stream()
                .map(Transaction::getTransactionTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(alert.getCreatedTime());
        String ruleSummary = buildRuleSummary(alert, ruleDetails);

        steps.add(newDispositionStep(
                "source",
                transactions.isEmpty() ? "规则/名单命中" : "关联交易命中",
                transactions.isEmpty() ? "由筛查、规则或人工线索触发" : transactions.size() + " 笔真实交易触发预警",
                transactions.isEmpty() ? ruleSummary : joinLimited(
                        transactions.stream().map(Transaction::getTransactionNo).collect(Collectors.toList()), 3),
                firstTransactionTime,
                "done"
        ));

        steps.add(newDispositionStep(
                "alert",
                "预警生成",
                alert.getAlertNo(),
                buildAlertMeta(alert, ruleSummary),
                alert.getCreatedTime(),
                isHighRisk(alert.getRiskLevel()) ? "warn" : "done"
        ));

        steps.add(newDispositionStep(
                "assign",
                "分派处理",
                alert.getAssignedTo() == null ? "尚未指派处理人" : "处理人：" + alert.getAssignedTo(),
                alert.getAssignedTime() == null ? "等待分派" : "已进入人工处理队列",
                alert.getAssignedTime(),
                alert.getAssignedTo() == null ? "current" : "done"
        ));

        steps.add(newDispositionStep(
                "review",
                "人工复核",
                alertProcessLabel(StringUtils.hasText(alert.getProcessResult()) ? alert.getProcessResult() : alert.getStatus()),
                StringUtils.hasText(alert.getProcessRemark()) ? alert.getProcessRemark() : "等待处理结论",
                alert.getProcessTime(),
                resolveReviewState(alert)
        ));

        steps.add(buildCaseStep(alert, cases));
        steps.add(buildStrReportStep(alert, strReports));
        return steps;
    }

    private AlertDispositionChainVO.Step buildCaseStep(Alert alert, List<Case> cases) {
        if (cases.isEmpty()) {
            boolean needsCase = AlertStatus.CONFIRMED.getCode().equals(alert.getStatus())
                    || AlertStatus.ESCALATED.getCode().equals(alert.getStatus());
            return newDispositionStep(
                    "case",
                    needsCase ? "案件待生成" : "案件闭环判断",
                    needsCase ? "该预警已进入可疑处置，需要形成案件留痕" : "未达到案件升级条件",
                    needsCase ? "请检查案件创建任务或人工补建案件" : "",
                    null,
                    needsCase ? "pending" : "muted"
            );
        }
        Case firstCase = cases.get(0);
        String statusSummary = joinLimited(cases.stream()
                .map(item -> item.getCaseNo() + "(" + caseStatusLabel(item.getCaseStatus()) + ")")
                .collect(Collectors.toList()), 3);
        return newDispositionStep(
                "case",
                "案件升级",
                statusSummary,
                firstCase.getSummary(),
                firstCase.getCreatedTime(),
                cases.stream().anyMatch(item -> "CLOSED".equals(item.getCaseStatus())) ? "done" : "warn"
        );
    }

    private AlertDispositionChainVO.Step buildStrReportStep(Alert alert, List<StrReport> strReports) {
        if (strReports.isEmpty()) {
            boolean needsReport = AlertStatus.CONFIRMED.getCode().equals(alert.getStatus());
            return newDispositionStep(
                    "str",
                    needsReport ? "STR待生成" : "STR报送判断",
                    needsReport ? "已确认可疑，建议生成可疑交易报告" : "未进入STR报送链路",
                    "",
                    null,
                    needsReport ? "pending" : "muted"
            );
        }
        boolean submitted = strReports.stream().anyMatch(this::isSubmittedToRegulator);
        StrReport firstReport = strReports.get(0);
        String reportSummary = joinLimited(strReports.stream()
                .map(item -> item.getReportNo() + "(" + reportStatusLabel(item.getReportStatus()) + ")")
                .collect(Collectors.toList()), 3);
        return newDispositionStep(
                "str",
                "STR报送",
                reportSummary,
                submitted ? "已完成监管报送或取得报送结果" : "报告已生成，等待复核/审批/报送",
                firstNonNull(firstReport.getSubmitTime(), firstReport.getCreatedTime()),
                submitted ? "done" : "current"
        );
    }

    private AlertDispositionChainVO.Step newDispositionStep(
            String key,
            String title,
            String subtitle,
            String meta,
            LocalDateTime time,
            String state
    ) {
        AlertDispositionChainVO.Step step = new AlertDispositionChainVO.Step();
        step.setKey(key);
        step.setTitle(title);
        step.setSubtitle(StringUtils.hasText(subtitle) ? subtitle : "-");
        step.setMeta(meta);
        step.setTime(time);
        step.setState(state);
        return step;
    }

    private String buildRuleSummary(Alert alert, List<AlertRuleDetail> ruleDetails) {
        List<String> ruleNames = ruleDetails.stream()
                .map(item -> StringUtils.hasText(item.getRuleName()) ? item.getRuleName() : item.getRuleCode())
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (!ruleNames.isEmpty()) {
            return "命中规则：" + joinLimited(ruleNames, 3);
        }
        if (StringUtils.hasText(alert.getSourceRuleCodes())) {
            return "规则代码：" + alert.getSourceRuleCodes();
        }
        return "规则明细待补充";
    }

    private String buildAlertMeta(Alert alert, String ruleSummary) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(alert.getAlertType())) {
            parts.add(alert.getAlertType());
        }
        if (alert.getRiskScore() != null) {
            parts.add("风险分 " + alert.getRiskScore());
        }
        if (StringUtils.hasText(ruleSummary)) {
            parts.add(ruleSummary);
        }
        return String.join(" / ", parts);
    }

    private String resolveReviewState(Alert alert) {
        if (AlertStatus.EXCLUDED.getCode().equals(alert.getStatus())) {
            return "muted";
        }
        if (AlertStatus.CONFIRMED.getCode().equals(alert.getStatus())) {
            return "done";
        }
        if (AlertStatus.ESCALATED.getCode().equals(alert.getStatus())) {
            return "warn";
        }
        return StringUtils.hasText(alert.getProcessResult()) ? "done" : "current";
    }

    private boolean isHighRisk(String riskLevel) {
        return RiskLevel.HIGH.getCode().equals(riskLevel) || RiskLevel.CRITICAL.getCode().equals(riskLevel);
    }

    private boolean isSubmittedToRegulator(StrReport report) {
        return "SUBMITTED".equals(report.getReportStatus())
                || "SUBMIT_SUCCESS".equals(report.getSubmitResult())
                || "SUCCESS".equals(report.getSubmitResult())
                || report.getSubmitTime() != null && StringUtils.hasText(report.getSubmitResult());
    }

    private LocalDateTime firstNonNull(LocalDateTime first, LocalDateTime second) {
        return first != null ? first : second;
    }

    private String joinLimited(List<String> values, int limit) {
        List<String> filtered = values.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (filtered.isEmpty()) {
            return "";
        }
        String joined = filtered.stream().limit(limit).collect(Collectors.joining("、"));
        if (filtered.size() > limit) {
            joined += " 等" + filtered.size() + "项";
        }
        return joined;
    }

    private String nullSafe(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String caseStatusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "-";
        }
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "INVESTIGATING" -> "调查中";
            case "PENDING_APPROVAL" -> "待审批";
            case "SUBMITTED" -> "已报送";
            case "CLOSED" -> "已结案";
            default -> status;
        };
    }

    private String reportStatusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "-";
        }
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "PENDING_REVIEW" -> "待审核";
            case "APPROVED" -> "已审核";
            case "REJECTED" -> "已拒绝";
            case "SUBMITTED" -> "已报送";
            default -> status;
        };
    }

    private String alertProcessLabel(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return switch (value) {
            case "CONFIRMED", "CONFIRMED_SUSPICIOUS" -> "确认可疑";
            case "EXCLUDED" -> "排除误报";
            case "ESCALATED" -> "升级处理";
            case "NEW" -> "新建";
            case "ASSIGNED" -> "已分配";
            case "PROCESSING" -> "处理中";
            default -> value;
        };
    }

    /**
     * 尝试自动分配单条预警（创建时调用）
     */
    private void tryAutoAssignSingleAlert(Alert alert) {
        try {
            List<Long> officerIds = getAvailableAmlOfficers();
            if (!CollectionUtils.isEmpty(officerIds)) {
                // 简单轮询：根据预警ID取模分配
                Long officerId = officerIds.get((int) (alert.getId() % officerIds.size()));
                doAssignAlert(alert, officerId, "AUTO", "创建时自动分配");
            }
        } catch (Exception e) {
            log.warn("自动分配预警失败，预警ID：{}，错误：{}", alert.getId(), e.getMessage());
        }
    }

    /**
     * 执行预警分配（更新预警信息并记录日志）
     */
    private void doAssignAlert(Alert alert, Long officerId, String assignType, String reason) {
        Long fromUserId = alert.getAssignedTo();

        alert.setAssignedTo(officerId);
        alert.setAssignedTime(LocalDateTime.now());
        alert.setStatus(AlertStatus.ASSIGNED.getCode());
        alert.setUpdatedTime(LocalDateTime.now());
        alertMapper.updateById(alert);

        saveAssignmentLog(alert.getId(), fromUserId, officerId, assignType, reason, "SYSTEM");
    }

    /**
     * 保存分配日志
     */
    private void saveAssignmentLog(Long alertId, Long fromUserId, Long toUserId,
                                   String assignType, String assignReason, String assignedBy) {
        AlertAssignmentLog logEntry = new AlertAssignmentLog();
        logEntry.setAlertId(alertId);
        logEntry.setFromUserId(fromUserId);
        logEntry.setToUserId(toUserId);
        logEntry.setAssignType(assignType);
        logEntry.setAssignReason(assignReason);
        logEntry.setAssignedBy(String.valueOf(assignedBy));
        logEntry.setAssignedTime(LocalDateTime.now());
        assignmentLogMapper.insert(logEntry);
    }

    /**
     * 根据已确认预警构造案件创建请求。
     */
    private CaseCreateRequest buildCaseCreateRequest(Alert alert) {
        CaseCreateRequest req = new CaseCreateRequest();
        req.setAlertId(alert.getId());
        req.setCaseType(alert.getAlertType());
        req.setPriority(resolveCasePriority(alert.getRiskLevel()));
        req.setSummary(StringUtils.hasText(alert.getAlertSummary())
                ? alert.getAlertSummary()
                : "预警确认可疑：" + alert.getAlertNo());
        return req;
    }

    private Integer resolveCasePriority(String riskLevel) {
        if (RiskLevel.CRITICAL.getCode().equals(riskLevel)) {
            return 5;
        }
        if (RiskLevel.HIGH.getCode().equals(riskLevel)) {
            return 4;
        }
        if (RiskLevel.MEDIUM.getCode().equals(riskLevel)) {
            return 3;
        }
        if (RiskLevel.LOW.getCode().equals(riskLevel)) {
            return 2;
        }
        return 3;
    }

    /**
     * 获取可用的AML专员ID列表。
     */
    private List<Long> getAvailableAmlOfficers() {
        List<Long> officerIds = sysUserMapper.findEnabledUserIdsByRoleCodes(List.of(
                "ROLE_AML_OFFICER",
                "ROLE_COMPLIANCE",
                "ROLE_INVESTIGATOR"
        ));
        if (CollectionUtils.isEmpty(officerIds)) {
            officerIds = sysUserMapper.findEnabledUserIdsByRoleCodes(List.of("ROLE_ADMIN"));
        }
        log.debug("查询可用AML专员，数量={}", officerIds == null ? 0 : officerIds.size());
        return officerIds == null ? Collections.emptyList() : officerIds;
    }

    /**
     * 获取升级处理管理员ID。
     */
    private Long getEscalationManager() {
        List<Long> managerIds = sysUserMapper.findEnabledUserIdsByRoleCodes(List.of(
                "ROLE_AML_MANAGER",
                "ROLE_ADMIN",
                "ROLE_COMPLIANCE"
        ));
        if (CollectionUtils.isEmpty(managerIds)) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "没有可用的预警升级管理员");
        }
        log.debug("查询升级管理员，userId={}", managerIds.get(0));
        return managerIds.get(0);
    }
}
