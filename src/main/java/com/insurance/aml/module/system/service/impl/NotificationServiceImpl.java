package com.insurance.aml.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.alert.mapper.AlertMapper;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.casemgmt.mapper.CaseMapper;
import com.insurance.aml.module.casemgmt.model.entity.Case;
import com.insurance.aml.module.system.mapper.SysNotificationMapper;
import com.insurance.aml.module.system.model.dto.NotificationVO;
import com.insurance.aml.module.system.model.entity.SysNotification;
import com.insurance.aml.module.system.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知管理服务实现
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class NotificationServiceImpl implements NotificationService {
    private static final String CASE_TYPE = "CASE";
    private static final String ALERT_TYPE = "ALERT";
    private static final Pattern CASE_NO_PATTERN = Pattern.compile("\\b(?:E2E)?CASE\\d+\\b");
    private static final Pattern ALERT_NO_PATTERN = Pattern.compile("\\b(?:E2E)?(?:ALT|AL)\\d+[A-Za-z0-9_-]*\\b");

    private final SysNotificationMapper sysNotificationMapper;
    private final CaseMapper caseMapper;
    private final AlertMapper alertMapper;

    /**
     * 发送通知：构建通知实体并写入数据库
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendNotification(Long userId, String type, String title, String content,
                                  String relatedType, String relatedId) {
        SysNotification notification = new SysNotification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedType(relatedType);
        notification.setRelatedId(relatedId);
        notification.setIsRead(false);
        notification.setCreatedTime(LocalDateTime.now());

        sysNotificationMapper.insert(notification);
        log.info("发送通知成功，userId={}, type={}, title={}", userId, type, title);
    }

    /**
     * 分页查询当前用户的通知，支持按已读状态筛选
     */
    @Override
    public PageResult<NotificationVO> getMyNotifications(Long userId, String type, Boolean isRead, PageQuery pageQuery) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getUserId, userId);

        if (StringUtils.hasText(type)) {
            wrapper.eq(SysNotification::getType, type);
        }
        if (isRead != null) {
            wrapper.eq(SysNotification::getIsRead, isRead);
        }

        wrapper.orderByDesc(SysNotification::getCreatedTime);

        IPage<SysNotification> page = sysNotificationMapper.selectPage(pageQuery.toPage(), wrapper);

        List<NotificationVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        PageResult result = new PageResult();
        result.setTotal(page.getTotal());
        result.setPage((int) page.getCurrent());
        result.setSize((int) page.getSize());
        result.setList(voList);
        return result;
    }

    /**
     * 标记单条通知为已读
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long notificationId, Long userId) {
        LambdaUpdateWrapper<SysNotification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysNotification::getId, notificationId)
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, false)
                .set(SysNotification::getIsRead, true)
                .set(SysNotification::getReadTime, LocalDateTime.now());

        sysNotificationMapper.update(null, wrapper);
        log.debug("标记通知已读，notificationId={}, userId={}", notificationId, userId);
    }

    /**
     * 标记用户所有未读通知为已读
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId) {
        LambdaUpdateWrapper<SysNotification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, false)
                .set(SysNotification::getIsRead, true)
                .set(SysNotification::getReadTime, LocalDateTime.now());

        sysNotificationMapper.update(null, wrapper);
        log.info("标记用户所有通知已读，userId={}", userId);
    }

    /**
     * 获取用户未读通知数量
     */
    @Override
    public long getUnreadCount(Long userId) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, false);

        return sysNotificationMapper.selectCount(wrapper);
    }

    /**
     * 实体转VO
     */
    private NotificationVO convertToVO(SysNotification entity) {
        NotificationVO vo = new NotificationVO();
        BeanUtils.copyProperties(entity, vo);
        enrichCaseReference(entity, vo);
        enrichAlertReference(entity, vo);
        return vo;
    }

    /**
     * 兼容历史案件通知：优先使用 relatedId 关联案件ID，缺失时从正文中的案件编号补齐。
     */
    private void enrichCaseReference(SysNotification entity, NotificationVO vo) {
        if (!CASE_TYPE.equals(entity.getType())) {
            return;
        }

        Case caseEntity = findRelatedCase(entity);
        if (caseEntity == null) {
            return;
        }

        vo.setRelatedType(CASE_TYPE);
        vo.setRelatedId(String.valueOf(caseEntity.getId()));
        vo.setCaseId(caseEntity.getId());
        vo.setCaseNo(caseEntity.getCaseNo());
        vo.setCaseCustomerName(caseEntity.getCustomerName());
        vo.setCaseStatus(caseEntity.getCaseStatus());
    }

    private Case findRelatedCase(SysNotification entity) {
        if (StringUtils.hasText(entity.getRelatedId())) {
            Case byId = findCaseById(entity.getRelatedId());
            if (byId != null) {
                return byId;
            }

            Case byCaseNo = findCaseByNo(entity.getRelatedId());
            if (byCaseNo != null) {
                return byCaseNo;
            }
        }

        String caseNo = extractCaseNo(entity.getContent());
        if (StringUtils.hasText(caseNo)) {
            return findCaseByNo(caseNo);
        }
        return null;
    }

    private Case findCaseById(String relatedId) {
        try {
            return caseMapper.selectById(Long.valueOf(relatedId));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Case findCaseByNo(String caseNo) {
        return caseMapper.selectOne(new LambdaQueryWrapper<Case>()
                .eq(Case::getCaseNo, caseNo)
                .last("LIMIT 1"));
    }

    private String extractCaseNo(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        Matcher matcher = CASE_NO_PATTERN.matcher(content);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * 兼容历史预警通知：优先使用 relatedId 关联预警ID，缺失时从正文或标题中的预警编号补齐。
     */
    private void enrichAlertReference(SysNotification entity, NotificationVO vo) {
        if (!ALERT_TYPE.equals(entity.getType())) {
            return;
        }

        Alert alert = findRelatedAlert(entity);
        if (alert == null) {
            return;
        }

        vo.setRelatedType(ALERT_TYPE);
        vo.setRelatedId(String.valueOf(alert.getId()));
        vo.setAlertId(alert.getId());
        vo.setAlertNo(alert.getAlertNo());
        vo.setAlertCustomerName(alert.getCustomerName());
        vo.setAlertType(alert.getAlertType());
        vo.setAlertRiskLevel(alert.getRiskLevel());
        vo.setAlertStatus(alert.getStatus());
    }

    private Alert findRelatedAlert(SysNotification entity) {
        if (StringUtils.hasText(entity.getRelatedId())) {
            Alert byId = findAlertById(entity.getRelatedId());
            if (byId != null) {
                return byId;
            }

            Alert byAlertNo = findAlertByNo(entity.getRelatedId());
            if (byAlertNo != null) {
                return byAlertNo;
            }
        }

        String alertNo = extractAlertNo(entity.getTitle() + " " + entity.getContent());
        if (StringUtils.hasText(alertNo)) {
            return findAlertByNo(alertNo);
        }
        return null;
    }

    private Alert findAlertById(String relatedId) {
        try {
            return alertMapper.selectById(Long.valueOf(relatedId));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Alert findAlertByNo(String alertNo) {
        return alertMapper.selectOne(new LambdaQueryWrapper<Alert>()
                .eq(Alert::getAlertNo, alertNo)
                .last("LIMIT 1"));
    }

    private String extractAlertNo(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        Matcher matcher = ALERT_NO_PATTERN.matcher(content);
        return matcher.find() ? matcher.group() : null;
    }
}
