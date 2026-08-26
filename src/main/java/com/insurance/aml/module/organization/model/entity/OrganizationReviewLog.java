package com.insurance.aml.module.organization.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 机构登记状态流转日志。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_aml_org_review_log")
public class OrganizationReviewLog extends BaseEntity {
    private Long registrationId;
    private String actionType;
    private String fromStatus;
    private String toStatus;
    private String opinion;
    private String operator;
    private LocalDateTime operatedAt;
}
