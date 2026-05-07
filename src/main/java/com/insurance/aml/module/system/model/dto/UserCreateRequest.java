package com.insurance.aml.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户创建请求
 */
@Data
@Schema(description = "用户创建请求参数")
public class UserCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户名（必填）
     */
    @Schema(description = "用户名")
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码（必填）
     */
    @Schema(description = "密码")
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 真实姓名（必填）
     */
    @Schema(description = "真实姓名")
    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱")
    private String email;

    /**
     * 手机号码
     */
    @Schema(description = "手机号码")
    private String phone;

    /**
     * 部门
     */
    @Schema(description = "部门")
    private String department;

    /**
     * 职位
     */
    @Schema(description = "职位")
    private String position;
}
