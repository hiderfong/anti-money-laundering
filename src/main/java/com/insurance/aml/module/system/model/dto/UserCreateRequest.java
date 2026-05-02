package com.insurance.aml.module.system.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户创建请求
 */
@Data
public class UserCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户名（必填）
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码（必填）
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 真实姓名（必填）
     */
    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号码
     */
    private String phone;

    /**
     * 部门
     */
    private String department;

    /**
     * 职位
     */
    private String position;
}
