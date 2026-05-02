package com.insurance.aml.module.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
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
import com.insurance.aml.module.case_.model.dto.CaseCreateRequest;
import com.insurance.aml.module.case_.service.CaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 预警管理服务实现类
 */
@Slf4j
@Service
public class AlertServiceImpl implements AlertService {

    @Autowired
    private AlertMapper alertMapper;

    @Autowired
    private AlertRuleDetailMapper alertRuleDetailMapper;

    @Autowired
    private AlertAssignmentLogMapper assignmentLogMapper;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private CaseService caseService;

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
        alert.setStatus("NEW");

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

        // 转换为VO并加载规则明细
        IPage<AlertVO> voPage = result.convert(alert -> {
            AlertVO vo = new AlertVO();
            BeanUtils.copyProperties(alert, vo);
            // 加载规则明细
            LambdaQueryWrapper<AlertRuleDetail> detailWrapper = new LambdaQueryWrapper<>();
            detailWrapper.eq(AlertRuleDetail::getAlertId, alert.getId());
            List<AlertRuleDetail> details = alertRuleDetailMapper.selectList(detailWrapper);
            vo.setRuleDetails(details);
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
        if (!"NEW".equals(alert.getStatus()) && !"ASSIGNED".equals(alert.getStatus())) {
            throw new BusinessException(ResultCode.ALERT_STATUS_ERROR,
                    "当前预警状态不允许分配，状态：" + alert.getStatus());
        }

        // 记录原处理人
        Long fromUserId = alert.getAssignedTo();

        // 更新预警分配信息
        alert.setAssignedTo(req.getAssignTo());
        alert.setAssignedTime(LocalDateTime.now());
        alert.setStatus("ASSIGNED");
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
        if (!"ASSIGNED".equals(alert.getStatus()) && !"PROCESSING".equals(alert.getStatus())) {
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
        switch (req.getProcessResult()) {
            case "CONFIRMED_SUSPICIOUS":
                alert.setStatus("CONFIRMED");
                shouldCreateCase = true;
                log.info("预警已确认可疑，预警ID：{}，准备创建案件", req.getAlertId());
                break;
            case "EXCLUDED":
                alert.setStatus("EXCLUDED");
                log.info("预警已排除，预警ID：{}", req.getAlertId());
                break;
            case "ESCALATED":
                alert.setStatus("ESCALATED");
                log.info("预警已升级，预警ID：{}", req.getAlertId());
                break;
            default:
                throw new BusinessException(ResultCode.BAD_REQUEST, "无效的处理结果：" + req.getProcessResult());
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
     * TODO: 需要依赖用户模块提供AML专员查询能力
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoAssignAlerts() {
        log.info("开始自动分配预警...");

        // 查询所有NEW状态的预警
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Alert::getStatus, "NEW");
        wrapper.orderByAsc(Alert::getCreatedTime);
        List<Alert> newAlerts = alertMapper.selectList(wrapper);

        if (CollectionUtils.isEmpty(newAlerts)) {
            log.info("没有待分配的预警");
            return;
        }

        log.info("待分配预警数量：{}", newAlerts.size());

        // 查询可用的AML专员（具有ROLE_AML_OFFICER角色的用户）
        // TODO: 需要用户模块提供查询AML专员的能力，当前使用占位逻辑
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
        wrapper.eq(Alert::getStatus, "ASSIGNED");
        wrapper.le(Alert::getAssignedTime, threshold);
        List<Alert> overdueAlerts = alertMapper.selectList(wrapper);

        if (CollectionUtils.isEmpty(overdueAlerts)) {
            log.info("没有超期预警");
            return;
        }

        log.info("超期预警数量：{}", overdueAlerts.size());

        // 获取管理员ID用于升级分配
        // TODO: 需要用户模块提供查询管理员的能力
        Long managerId = getEscalationManager();

        for (Alert alert : overdueAlerts) {
            Long fromUserId = alert.getAssignedTo();

            // 更新预警状态为ESCALATED
            alert.setStatus("ESCALATED");
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
        String[] statuses = {"NEW", "ASSIGNED", "PROCESSING", "CONFIRMED", "EXCLUDED", "ESCALATED"};
        for (String status : statuses) {
            LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Alert::getStatus, status);
            Long count = alertMapper.selectCount(wrapper);
            statusCount.put(status, count);
        }
        statistics.setCountByStatus(statusCount);

        // 统计各风险等级数量
        Map<String, Long> riskLevelCount = new HashMap<>();
        String[] riskLevels = {"LOW", "MEDIUM", "HIGH", "CRITICAL"};
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
        alert.setStatus("ASSIGNED");
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
        if ("CRITICAL".equals(riskLevel)) {
            return 5;
        }
        if ("HIGH".equals(riskLevel)) {
            return 4;
        }
        if ("MEDIUM".equals(riskLevel)) {
            return 3;
        }
        if ("LOW".equals(riskLevel)) {
            return 2;
        }
        return 3;
    }

    /**
     * 获取可用的AML专员ID列表
     * TODO: 需要用户模块提供查询AML专员的能力，当前返回测试数据
     */
    private List<Long> getAvailableAmlOfficers() {
        // TODO: 替换为实际的用户查询逻辑
        // 例如：查询 sys_user 表中具有 ROLE_AML_OFFICER 角色的启用用户
        // return userMapper.selectUserIdsByRole("ROLE_AML_OFFICER");
        log.debug("查询可用AML专员（当前使用占位逻辑）");
        return new ArrayList<>();
    }

    /**
     * 获取升级处理的管理员ID
     * TODO: 需要用户模块提供查询管理员的能力，当前返回默认值
     */
    private Long getEscalationManager() {
        // TODO: 替换为实际的管理员查询逻辑
        // 例如：查询 sys_user 表中具有 ROLE_MANAGER 角色的第一个用户
        // return userMapper.selectFirstUserIdByRole("ROLE_MANAGER");
        log.debug("查询升级管理员（当前使用占位逻辑）");
        return 1L; // 默认管理员ID
    }
}
