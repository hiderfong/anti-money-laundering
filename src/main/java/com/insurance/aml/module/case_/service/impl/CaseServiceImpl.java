package com.insurance.aml.module.case_.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    @Lazy
    private final com.insurance.aml.module.alert.mapper.AlertMapper alertMapper;
    private final IdGenerator idGenerator;

    /**
     * 合法的状态流转映射
     * key: 当前状态, value: 允许流转到的目标状态列表
     */
    private static final Map<String, List<String>> VALID_TRANSITIONS = new HashMap<>();

    static {
        VALID_TRANSITIONS.put("DRAFT", Arrays.asList("INVESTIGATING"));
        VALID_TRANSITIONS.put("INVESTIGATING", Arrays.asList("PENDING_APPROVAL"));
        VALID_TRANSITIONS.put("PENDING_APPROVAL", Arrays.asList("SUBMITTED", "INVESTIGATING"));
        VALID_TRANSITIONS.put("SUBMITTED", Arrays.asList("CLOSED"));
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
        if (!"CONFIRMED".equals(alert.getStatus())) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "只有已确认的告警才能创建案件，当前状态=" + alert.getStatus());
        }

        // 创建案件实体
        Case caseEntity = new Case();
        caseEntity.setCaseNo(idGenerator.generateCaseNo());
        caseEntity.setAlertId(req.getAlertId());
        caseEntity.setCustomerId(alert.getCustomerId());
        caseEntity.setCustomerName(alert.getCustomerName());
        caseEntity.setCaseStatus("DRAFT");
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
        saveStatusLog(caseEntity.getId(), null, "DRAFT", "创建案件", SecurityUtils.getCurrentUsername());

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
        if ("SUBMITTED".equals(toStatus)) {
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

        // 转换为VO
        IPage<CaseVO> voPage = page.convert(caseEntity -> {
            CaseVO vo = new CaseVO();
            BeanUtils.copyProperties(caseEntity, vo);

            // 查询调查记录数
            Long investigationCount = caseInvestigationMapper.selectCount(
                    new LambdaQueryWrapper<CaseInvestigation>()
                            .eq(CaseInvestigation::getCaseId, caseEntity.getId())
            );
            vo.setInvestigationCount(investigationCount.intValue());

            // 查询是否有STR报告
            Long strCount = strReportMapper.selectCount(
                    new LambdaQueryWrapper<StrReport>()
                            .eq(StrReport::getCaseId, caseEntity.getId())
            );
            vo.setHasStrReport(strCount > 0);

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
        if (!"SUBMITTED".equals(caseEntity.getCaseStatus())) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "只有已提交的案件才能关闭，当前状态=" + caseEntity.getCaseStatus());
        }

        String fromStatus = caseEntity.getCaseStatus();

        // 更新案件为已关闭
        caseEntity.setCaseStatus("CLOSED");
        caseEntity.setCloseTime(LocalDateTime.now());
        caseEntity.setCloseReason(reason);
        caseEntity.setUpdatedBy(SecurityUtils.getCurrentUsername());
        caseEntity.setUpdatedTime(LocalDateTime.now());

        caseMapper.updateById(caseEntity);

        // 记录状态变更日志
        saveStatusLog(caseId, fromStatus, "CLOSED", reason, SecurityUtils.getCurrentUsername());

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
}
