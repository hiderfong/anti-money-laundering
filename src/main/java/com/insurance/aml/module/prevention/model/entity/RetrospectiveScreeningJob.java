package com.insurance.aml.module.prevention.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回溯筛查任务。
 */
@Data
@TableName("t_retrospective_screening_job")
public class RetrospectiveScreeningJob extends BaseEntity {

    private String jobNo;
    private String jobName;
    private String scopeType;
    private String customerIds;
    private Long watchlistSourceId;
    private String status;
    private Integer totalCustomers;
    private Integer processedCustomers;
    private Integer totalHits;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private String remark;
}
