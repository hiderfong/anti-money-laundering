package com.insurance.aml.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.system.mapper.SysNotificationMapper;
import com.insurance.aml.module.system.model.dto.NotificationVO;
import com.insurance.aml.module.system.model.entity.SysNotification;
import com.insurance.aml.module.system.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通知管理服务实现
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private SysNotificationMapper sysNotificationMapper;

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
    public PageResult<NotificationVO> getMyNotifications(Long userId, Boolean isRead, PageQuery pageQuery) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getUserId, userId);

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
        return vo;
    }
}
