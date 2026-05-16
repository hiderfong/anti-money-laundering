package com.insurance.aml.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知视图对象
 */
@Data
@Schema(description = "通知视图对象")
public class NotificationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Schema(description = "主键ID")
    private Long id;

    /**
     * 接收用户ID
     */
    @Schema(description = "接收用户ID")
    private Long userId;

    /**
     * 通知类型
     */
    @Schema(description = "通知类型")
    private String type;

    /**
     * 通知标题
     */
    @Schema(description = "通知标题")
    private String title;

    /**
     * 通知内容
     */
    @Schema(description = "通知内容")
    private String content;

    /**
     * 关联业务类型
     */
    @Schema(description = "关联业务类型")
    private String relatedType;

    /**
     * 关联业务ID
     */
    @Schema(description = "关联业务ID")
    private String relatedId;

    /**
     * 是否已读
     */
    @Schema(description = "是否已读")
    private Boolean isRead;

    /**
     * 阅读时间
     */
    @Schema(description = "阅读时间")
    private LocalDateTime readTime;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
}
