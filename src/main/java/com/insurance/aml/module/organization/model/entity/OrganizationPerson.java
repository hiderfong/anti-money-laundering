package com.insurance.aml.module.organization.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 机构高级管理人员、反洗钱人员及联络人。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_aml_org_person")
public class OrganizationPerson extends BaseEntity {
    private Long organizationId;
    private String personType;
    private String personName;
    private String title;
    private String department;
    private String phone;
    private String email;
    private LocalDate startDate;
    private LocalDate endDate;
    private String financialExperience;
    private Boolean primaryFlag;
    private String status;
}
