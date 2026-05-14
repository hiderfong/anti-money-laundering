package com.insurance.aml.module.prevention.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 名单更新任务。
 */
@Data
@TableName("t_watchlist_update_job")
public class WatchlistUpdateJob extends BaseEntity {

    private String jobNo;
    private Long sourceId;
    private String sourceName;
    private String updateMode;
    private String status;
    private Integer totalEntries;
    private Integer addedCount;
    private Integer updatedCount;
    private Integer expiredCount;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private String errorMessage;
}
