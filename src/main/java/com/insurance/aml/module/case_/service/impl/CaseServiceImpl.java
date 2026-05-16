package com.insurance.aml.module.case_.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.enums.AlertStatus;
import com.insurance.aml.common.enums.CaseStatus;
import com.insurance.aml.common.enums.StatusEnum;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.case_.mapper.CaseAttachmentMapper;
import com.insurance.aml.module.case_.mapper.CaseInvestigationMapper;
import com.insurance.aml.module.case_.mapper.CaseMapper;
import com.insurance.aml.module.case_.mapper.CaseStatusLogMapper;
import com.insurance.aml.module.case_.mapper.StrReportMapper;
import com.insurance.aml.module.case_.model.dto.CaseCreateRequest;
import com.insurance.aml.module.case_.model.dto.CaseDetailVO;
import com.insurance.aml.module.case_.model.dto.CaseQueryRequest;
import com.insurance.aml.module.case_.model.dto.CaseVO;
import com.insurance.aml.module.case_.model.entity.Case;
import com.insurance.aml.module.case_.model.entity.CaseAttachment;
import com.insurance.aml.module.case_.model.entity.CaseInvestigation;
import com.insurance.aml.module.case_.model.entity.CaseStatusLog;
import com.insurance.aml.module.case_.model.entity.StrReport;
import com.insurance.aml.module.case_.service.CaseService;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.system.mapper.SysUserMapper;
import com.insurance.aml.module.system.model.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 案件管理服务实现
 * 处理案件的创建、状态流转、调查记录管理等核心业务逻辑
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CaseServiceImpl implements CaseService {

    private final CaseMapper caseMapper;
    private final CaseStatusLogMapper caseStatusLogMapper;
    private final CaseInvestigationMapper caseInvestigationMapper;
    private final CaseAttachmentMapper caseAttachmentMapper;
    private final StrReportMapper strReportMapper;
    private final CustomerMapper customerMapper;
    private final SysUserMapper sysUserMapper;
    @Lazy
    private final com.insurance.aml.module.alert.mapper.AlertMapper alertMapper;
    private final IdGenerator idGenerator;

    private static final Map<String, String> OPERATOR_DISPLAY_NAMES = Map.of(
            "admin", "系统管理员",
            "system", "系统自动处理",
            "e2e_admin", "系统管理员",
            "e2e_seed_operator", "合规负责人",
            "e2e_compliance", "合规审批员",
            "e2e_investigator", "案件调查员",
            "e2e_viewer", "只读观察员",
            "e2e", "系统初始化"
    );

    /**
     * 合法的状态流转映射
     * key: 当前状态, value: 允许流转到的目标状态列表
     */
    private static final Map<String, List<String>> VALID_TRANSITIONS = new HashMap<>();

    static {
        VALID_TRANSITIONS.put(CaseStatus.DRAFT.getCode(), Arrays.asList(CaseStatus.INVESTIGATING.getCode()));
        VALID_TRANSITIONS.put(CaseStatus.INVESTIGATING.getCode(), Arrays.asList(CaseStatus.PENDING_APPROVAL.getCode()));
        VALID_TRANSITIONS.put(CaseStatus.PENDING_APPROVAL.getCode(), Arrays.asList(CaseStatus.SUBMITTED.getCode(), CaseStatus.INVESTIGATING.getCode()));
        VALID_TRANSITIONS.put(CaseStatus.SUBMITTED.getCode(), Arrays.asList(CaseStatus.CLOSED.getCode()));
        // CLOSED 为终态，不可再流转
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Case createCase(CaseCreateRequest req) {
        log.info("创建案件，alertId={}", req.getAlertId());

        // 查询关联告警，验证告警状态为CONFIRMED
        var alert = alertMapper.selectById(req.getAlertId());
        if (alert == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "关联告警不存在，alertId=" + req.getAlertId());
        }
        if (!AlertStatus.CONFIRMED.getCode().equals(alert.getStatus())) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "只有已确认的告警才能创建案件，当前状态=" + alert.getStatus());
        }

        // 创建案件实体
        Case caseEntity = new Case();
        caseEntity.setCaseNo(idGenerator.generateCaseNo());
        caseEntity.setAlertId(req.getAlertId());
        caseEntity.setCustomerId(alert.getCustomerId());
        caseEntity.setCustomerName(alert.getCustomerName());
        caseEntity.setCaseStatus(CaseStatus.DRAFT.getCode());
        caseEntity.setCaseType(req.getCaseType());
        caseEntity.setPriority(req.getPriority() != null ? req.getPriority() : 3);
        caseEntity.setSummary(req.getSummary());
        caseEntity.setCreatedBy(SecurityUtils.getCurrentUsername());
        caseEntity.setCreatedTime(LocalDateTime.now());
        caseEntity.setUpdatedBy(SecurityUtils.getCurrentUsername());
        caseEntity.setUpdatedTime(LocalDateTime.now());

        caseMapper.insert(caseEntity);
        log.info("案件创建成功，caseId={}, caseNo={}", caseEntity.getId(), caseEntity.getCaseNo());

        // 记录状态变更日志：null → DRAFT
        saveStatusLog(caseEntity.getId(), null, CaseStatus.DRAFT.getCode(), "创建案件", SecurityUtils.getCurrentUsername());

        return caseEntity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeCaseStatus(Long caseId, String toStatus, String remark) {
        log.info("变更案件状态，caseId={}, toStatus={}", caseId, toStatus);

        Case caseEntity = caseMapper.selectById(caseId);
        if (caseEntity == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "案件不存在，caseId=" + caseId);
        }

        String currentStatus = caseEntity.getCaseStatus();

        // 校验状态流转合法性
        List<String> allowedTargets = VALID_TRANSITIONS.get(currentStatus);
        if (allowedTargets == null || !allowedTargets.contains(toStatus)) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    String.format("非法状态流转：%s → %s", currentStatus, toStatus));
        }

        // 更新案件状态
        caseEntity.setCaseStatus(toStatus);
        caseEntity.setUpdatedBy(SecurityUtils.getCurrentUsername());
        caseEntity.setUpdatedTime(LocalDateTime.now());

        // 如果流转到已提交，记录提交时间
        if (CaseStatus.SUBMITTED.getCode().equals(toStatus)) {
            caseEntity.setSubmitTime(LocalDateTime.now());
        }

        caseMapper.updateById(caseEntity);

        // 记录状态变更日志
        saveStatusLog(caseId, currentStatus, toStatus, remark, SecurityUtils.getCurrentUsername());

        log.info("案件状态变更成功，caseId={}, {} → {}", caseId, currentStatus, toStatus);
    }

    @Override
    public CaseDetailVO getCaseDetail(Long caseId) {
        log.info("查询案件详情，caseId={}", caseId);

        Case caseEntity = caseMapper.selectById(caseId);
        if (caseEntity == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "案件不存在，caseId=" + caseId);
        }

        // 构建详情VO
        CaseDetailVO detailVO = new CaseDetailVO();
        BeanUtils.copyProperties(caseEntity, detailVO);
        Map<Long, String> customerNameMap = needsCustomerNameFallback(caseEntity)
                ? loadCustomerNameMap(Collections.singletonList(caseEntity.getCustomerId()))
                : Collections.emptyMap();
        Map<String, String> operatorNameMap = loadOperatorNameMap(Arrays.asList(caseEntity.getCreatedBy(), caseEntity.getUpdatedBy()));
        enrichCaseVO(detailVO, caseEntity, customerNameMap, operatorNameMap);

        // 查询调查记录
        List<CaseInvestigation> investigations = caseInvestigationMapper.selectList(
                new LambdaQueryWrapper<CaseInvestigation>()
                        .eq(CaseInvestigation::getCaseId, caseId)
                        .orderByDesc(CaseInvestigation::getCreatedTime)
        );
        detailVO.setInvestigations(investigations);
        detailVO.setInvestigationCount(investigations.size());

        // 查询附件
        List<CaseAttachment> attachments = caseAttachmentMapper.selectList(
                new LambdaQueryWrapper<CaseAttachment>()
                        .eq(CaseAttachment::getCaseId, caseId)
                        .orderByDesc(CaseAttachment::getUploadTime)
        );
        detailVO.setAttachments(attachments);

        // 查询可疑交易报告
        StrReport strReport = strReportMapper.selectOne(
                new LambdaQueryWrapper<StrReport>()
                        .eq(StrReport::getCaseId, caseId)
                        .last("LIMIT 1")
        );
        detailVO.setStrReport(strReport);
        detailVO.setHasStrReport(strReport != null);

        // 查询状态变更日志
        List<CaseStatusLog> statusLogs = caseStatusLogMapper.selectList(
                new LambdaQueryWrapper<CaseStatusLog>()
                        .eq(CaseStatusLog::getCaseId, caseId)
                        .orderByDesc(CaseStatusLog::getChangedTime)
        );
        Map<String, String> statusOperatorNameMap = loadOperatorNameMap(
                statusLogs.stream().map(CaseStatusLog::getChangedBy).collect(Collectors.toList())
        );
        statusLogs.forEach(log -> log.setChangedBy(resolveOperatorDisplayName(log.getChangedBy(), statusOperatorNameMap)));
        detailVO.setStatusLogs(statusLogs);

        return detailVO;
    }

    @Override
    public PageResult<CaseVO> pageQueryCases(CaseQueryRequest req) {
        log.info("分页查询案件，page={}, size={}", req.getPage(), req.getSize());

        LambdaQueryWrapper<Case> wrapper = new LambdaQueryWrapper<>();

        // 按状态过滤
        if (StringUtils.hasText(req.getCaseStatus())) {
            wrapper.eq(Case::getCaseStatus, req.getCaseStatus());
        }
        // 按类型过滤
        if (StringUtils.hasText(req.getCaseType())) {
            wrapper.eq(Case::getCaseType, req.getCaseType());
        }
        // 按优先级过滤
        if (req.getPriority() != null) {
            wrapper.eq(Case::getPriority, req.getPriority());
        }
        // 按客户ID过滤
        if (req.getCustomerId() != null) {
            wrapper.eq(Case::getCustomerId, req.getCustomerId());
        }
        // 按调查员过滤
        if (req.getInvestigatorId() != null) {
            wrapper.eq(Case::getInvestigatorId, req.getInvestigatorId());
        }
        // 按时间范围过滤
        if (StringUtils.hasText(req.getStartTime())) {
            wrapper.ge(Case::getCreatedTime, LocalDateTime.parse(req.getStartTime(), DateTimeFormatter.ISO_DATE_TIME));
        }
        if (StringUtils.hasText(req.getEndTime())) {
            wrapper.le(Case::getCreatedTime, LocalDateTime.parse(req.getEndTime(), DateTimeFormatter.ISO_DATE_TIME));
        }

        // 按创建时间倒序
        wrapper.orderByDesc(Case::getCreatedTime);

        IPage<Case> page = caseMapper.selectPage(req.toPage(), wrapper);

        List<Case> cases = page.getRecords();

        // 批量查询关联数据（消除N+1）
        Map<Long, Long> investigationCountMap = Collections.emptyMap();
        Set<Long> caseIdsWithStr = Collections.emptySet();
        Map<Long, String> customerNameMap = Collections.emptyMap();
        Map<String, String> operatorNameMap = Collections.emptyMap();

        if (!cases.isEmpty()) {
            List<Long> caseIds = cases.stream().map(Case::getId).collect(Collectors.toList());
            List<Long> customerIds = cases.stream()
                    .filter(this::needsCustomerNameFallback)
                    .map(Case::getCustomerId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            List<String> operatorKeys = cases.stream()
                    .flatMap(caseEntity -> Arrays.asList(caseEntity.getCreatedBy(), caseEntity.getUpdatedBy()).stream())
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
            customerNameMap = loadCustomerNameMap(customerIds);
            operatorNameMap = loadOperatorNameMap(operatorKeys);

            // 批量查询调查记录，按caseId分组计数
            List<CaseInvestigation> investigations = caseInvestigationMapper.selectList(
                    new LambdaQueryWrapper<CaseInvestigation>()
                            .in(CaseInvestigation::getCaseId, caseIds)
                            .select(CaseInvestigation::getCaseId)
            );
            investigationCountMap = investigations.stream()
                    .collect(Collectors.groupingBy(CaseInvestigation::getCaseId, Collectors.counting()));

            // 批量查询STR报告，只需知道哪些caseId有报告
            List<StrReport> strReports = strReportMapper.selectList(
                    new LambdaQueryWrapper<StrReport>()
                            .in(StrReport::getCaseId, caseIds)
                            .select(StrReport::getCaseId)
            );
            caseIdsWithStr = strReports.stream()
                    .map(StrReport::getCaseId)
                    .collect(Collectors.toSet());
        }

        // 转换为VO并组装关联数据
        final Map<Long, Long> finalInvestigationCountMap = investigationCountMap;
        final Set<Long> finalCaseIdsWithStr = caseIdsWithStr;
        final Map<Long, String> finalCustomerNameMap = customerNameMap;
        final Map<String, String> finalOperatorNameMap = operatorNameMap;
        IPage<CaseVO> voPage = page.convert(caseEntity -> {
            CaseVO vo = new CaseVO();
            BeanUtils.copyProperties(caseEntity, vo);
            enrichCaseVO(vo, caseEntity, finalCustomerNameMap, finalOperatorNameMap);
            vo.setInvestigationCount(finalInvestigationCountMap.getOrDefault(caseEntity.getId(), 0L).intValue());
            vo.setHasStrReport(finalCaseIdsWithStr.contains(caseEntity.getId()));
            return vo;
        });

        return PageResult.from(voPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addInvestigation(Long caseId, String content, String conclusion) {
        log.info("添加调查记录，caseId={}", caseId);

        // 校验案件存在
        Case caseEntity = caseMapper.selectById(caseId);
        if (caseEntity == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "案件不存在，caseId=" + caseId);
        }

        // 保存调查记录
        CaseInvestigation investigation = new CaseInvestigation();
        investigation.setCaseId(caseId);
        investigation.setContent(content);
        investigation.setConclusion(conclusion);
        investigation.setInvestigatorId(SecurityUtils.getCurrentUserId());
        investigation.setCreatedTime(LocalDateTime.now());

        caseInvestigationMapper.insert(investigation);
        log.info("调查记录添加成功，investigationId={}", investigation.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeCase(Long caseId, String reason) {
        log.info("关闭案件，caseId={}, reason={}", caseId, reason);

        Case caseEntity = caseMapper.selectById(caseId);
        if (caseEntity == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "案件不存在，caseId=" + caseId);
        }

        // 只有已提交的案件才能关闭
        if (!CaseStatus.SUBMITTED.getCode().equals(caseEntity.getCaseStatus())) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "只有已提交的案件才能关闭，当前状态=" + caseEntity.getCaseStatus());
        }

        String fromStatus = caseEntity.getCaseStatus();

        // 更新案件为已关闭
        caseEntity.setCaseStatus(CaseStatus.CLOSED.getCode());
        caseEntity.setCloseTime(LocalDateTime.now());
        caseEntity.setCloseReason(reason);
        caseEntity.setUpdatedBy(SecurityUtils.getCurrentUsername());
        caseEntity.setUpdatedTime(LocalDateTime.now());

        caseMapper.updateById(caseEntity);

        // 记录状态变更日志
        saveStatusLog(caseId, fromStatus, CaseStatus.CLOSED.getCode(), reason, SecurityUtils.getCurrentUsername());

        log.info("案件关闭成功，caseId={}", caseId);
    }

    /**
     * 保存状态变更日志
     */
    private void saveStatusLog(Long caseId, String fromStatus, String toStatus, String remark, String changedBy) {
        CaseStatusLog statusLog = new CaseStatusLog();
        statusLog.setCaseId(caseId);
        statusLog.setFromStatus(fromStatus);
        statusLog.setToStatus(toStatus);
        statusLog.setRemark(remark);
        statusLog.setChangedBy(changedBy);
        statusLog.setChangedTime(LocalDateTime.now());

        caseStatusLogMapper.insert(statusLog);
    }

    private void enrichCaseVO(CaseVO vo,
                              Case caseEntity,
                              Map<Long, String> customerNameMap,
                              Map<String, String> operatorNameMap) {
        vo.setCustomerName(resolveCustomerName(caseEntity, customerNameMap));
        vo.setCreatedBy(resolveOperatorDisplayName(caseEntity.getCreatedBy(), operatorNameMap));
        vo.setUpdatedBy(resolveOperatorDisplayName(caseEntity.getUpdatedBy(), operatorNameMap));
    }

    private Map<Long, String> loadCustomerNameMap(Collection<Long> customerIds) {
        List<Long> ids = customerIds == null ? Collections.emptyList() : customerIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Customer> customers = customerMapper.selectList(
                new LambdaQueryWrapper<Customer>()
                        .in(Customer::getId, ids)
                        .select(Customer::getId, Customer::getName)
        );
        if (customers == null) {
            return Collections.emptyMap();
        }

        return customers.stream()
                .filter(customer -> customer.getId() != null && StringUtils.hasText(customer.getName()))
                .collect(Collectors.toMap(Customer::getId, Customer::getName, (first, second) -> first));
    }

    private Map<String, String> loadOperatorNameMap(Collection<String> operatorKeys) {
        List<String> usernames = operatorKeys == null ? Collections.emptyList() : operatorKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(key -> !OPERATOR_DISPLAY_NAMES.containsKey(key))
                .filter(key -> !isTestMarker(key))
                .distinct()
                .collect(Collectors.toList());
        if (usernames.isEmpty()) {
            return Collections.emptyMap();
        }

        List<SysUser> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getUsername, usernames)
                        .select(SysUser::getUsername, SysUser::getRealName)
        );
        if (users == null) {
            return Collections.emptyMap();
        }

        return users.stream()
                .filter(user -> StringUtils.hasText(user.getUsername()) && StringUtils.hasText(user.getRealName()))
                .collect(Collectors.toMap(SysUser::getUsername, SysUser::getRealName, (first, second) -> first));
    }

    private String resolveCustomerName(Case caseEntity, Map<Long, String> customerNameMap) {
        String snapshotName = trimToEmpty(caseEntity.getCustomerName());
        if (StringUtils.hasText(snapshotName) && !isLegacyCustomerName(snapshotName)) {
            return snapshotName;
        }

        String currentName = customerNameMap.get(caseEntity.getCustomerId());
        if (StringUtils.hasText(currentName)) {
            return currentName;
        }

        if (caseEntity.getCustomerId() != null) {
            return "客户ID " + caseEntity.getCustomerId();
        }
        return "-";
    }

    private boolean needsCustomerNameFallback(Case caseEntity) {
        String snapshotName = trimToEmpty(caseEntity.getCustomerName());
        return !StringUtils.hasText(snapshotName) || isLegacyCustomerName(snapshotName);
    }

    private String resolveOperatorDisplayName(String operator, Map<String, String> operatorNameMap) {
        String key = trimToEmpty(operator);
        if (!StringUtils.hasText(key)) {
            return "-";
        }

        String mapped = OPERATOR_DISPLAY_NAMES.get(key);
        if (mapped != null) {
            return mapped;
        }

        String lowerKey = key.toLowerCase();
        if (lowerKey.contains("compliance")) {
            return "合规审批员";
        }
        if (lowerKey.contains("investigator")) {
            return "案件调查员";
        }
        if (lowerKey.contains("viewer")) {
            return "只读观察员";
        }
        if (lowerKey.contains("seed") || lowerKey.contains("business-seed")) {
            return "合规负责人";
        }
        if (isTestMarker(key)) {
            return "系统操作员";
        }

        String realName = operatorNameMap.get(key);
        if (StringUtils.hasText(realName) && !isTestMarker(realName)) {
            return realName;
        }
        return key;
    }

    private boolean isLegacyCustomerName(String value) {
        String trimmed = trimToEmpty(value);
        String lower = trimmed.toLowerCase();
        return trimmed.matches("^客户\\d+$")
                || lower.contains("e2e")
                || isMojibakeText(trimmed);
    }

    private boolean isTestMarker(String value) {
        String lower = trimToEmpty(value).toLowerCase();
        return lower.contains("e2e") || lower.contains("test");
    }

    private boolean isMojibakeText(String value) {
        return value.contains("å") || value.contains("æ") || value.contains("è")
                || value.contains("é") || value.contains("ç") || value.contains("ä");
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
