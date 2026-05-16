package com.insurance.aml.module.system.controller;

import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.system.model.dto.NotificationVO;
import com.insurance.aml.module.system.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 通知管理控制器
 * 提供通知查询、标记已读等接口
 */
@Slf4j
@RestController
@RequestMapping("/system/notifications")
@RequiredArgsConstructor
@Tag(name = "通知管理", description = "通知管理相关接口")
public class NotificationController {
    private final NotificationService notificationService;

    /**
     * 获取当前用户的通知列表（分页）
     */
    @GetMapping("/my")
    @Operation(summary = "我的通知", description = "分页查询当前登录用户的通知，支持按已读状态筛选")
    public Result<PageResult<NotificationVO>> getMyNotifications(
            @Parameter(description = "通知类型") @RequestParam(required = false) String type,
            @Parameter(description = "是否已读") @RequestParam(required = false) Boolean isRead,
            PageQuery pageQuery) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("查询我的通知，userId={}", userId);
        PageResult<NotificationVO> result = notificationService.getMyNotifications(userId, type, isRead, pageQuery);
        return Result.success(result);
    }

    /**
     * 标记单条通知为已读
     */
    @PostMapping("/{id}/read")
    @Operation(summary = "标记已读", description = "将指定通知标记为已读")
    public Result<Void> markAsRead(
            @Parameter(description = "通知ID") @PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        notificationService.markAsRead(id, userId);
        return Result.success();
    }

    /**
     * 标记当前用户所有未读通知为已读
     */
    @PostMapping("/read-all")
    @Operation(summary = "全部已读", description = "将当前用户所有未读通知标记为已读")
    public Result<Void> markAllAsRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("标记所有通知已读，userId={}", userId);
        notificationService.markAllAsRead(userId);
        return Result.success();
    }

    /**
     * 获取当前用户未读通知数量
     */
    @GetMapping("/unread-count")
    @Operation(summary = "未读数量", description = "获取当前用户的未读通知数量")
    public Result<Long> getUnreadCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        long count = notificationService.getUnreadCount(userId);
        return Result.success(count);
    }
}
