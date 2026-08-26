package com.insurance.aml.module.organization.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

/**
 * 机构治理人员维护请求。
 */
@Data
public class OrganizationPersonRequest {
    @NotBlank(message = "人员类型不能为空")
    @Pattern(regexp = "SENIOR_MANAGER|AML_OFFICER|CONTACT", message = "人员类型不正确")
    private String personType;

    @NotBlank(message = "姓名不能为空")
    private String personName;
    private String title;
    private String department;
    private String phone;

    @Email(message = "邮箱格式不正确")
    private String email;

    private LocalDate startDate;
    private LocalDate endDate;
    private String financialExperience;
    private Boolean primaryFlag;

    @Pattern(regexp = "ENABLED|DISABLED", message = "人员状态不正确")
    private String status;
}
