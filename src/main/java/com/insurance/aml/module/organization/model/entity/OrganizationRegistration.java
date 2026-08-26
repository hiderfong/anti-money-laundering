package com.insurance.aml.module.organization.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 机构登记申请。snapshotJson 固化提交时的完整机构信息，保证审批证据可追溯。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_aml_org_registration")
public class OrganizationRegistration extends BaseEntity {
    private String registrationNo;
    private Long organizationId;
    private String registrationType;
    private Integer version;
    private String status;
    private Boolean commitmentAccepted;
    private String snapshotJson;
    private String submittedBy;
    private LocalDateTime submittedAt;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewOpinion;
}
