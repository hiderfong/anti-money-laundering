package com.insurance.aml.module.system.service;

import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.system.model.dto.NotificationVO;

/**
 * 通知管理服务接口
 */
public interface NotificationService {

    /**
     * 发送通知
     *
     * @param userId      接收用户ID
     * @param type        通知类型
     * @param title       通知标题
     * @param content     通知内容
     * @param relatedType 关联业务类型
     * @param relatedId   关联业务ID
     */
    void sendNotification(Long userId, String type, String title, String content,
                          String relatedType, String relatedId);

    /**
     * 分页查询当前用户的通知
     *
     * @param userId    用户ID
     * @param isRead    是否已读（可选，null表示查询全部）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<NotificationVO> getMyNotifications(Long userId, Boolean isRead, PageQuery pageQuery);

    /**
     * 标记单条通知为已读
     *
     * @param notificationId 通知ID
     * @param userId         用户ID（校验归属）
     */
    void markAsRead(Long notificationId, Long userId);

    /**
     * 标记用户所有未读通知为已读
     *
     * @param userId 用户ID
     */
    void markAllAsRead(Long userId);

    /**
     * 获取用户未读通知数量
     *
     * @param userId 用户ID
     * @return 未读数量
     */
    long getUnreadCount(Long userId);
}
