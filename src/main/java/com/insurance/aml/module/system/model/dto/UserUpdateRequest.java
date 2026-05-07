package com.insurance.aml.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户更新请求
 */
@Data
@Schema(description = "用户更新请求参数")
public class UserUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID（必填）
     */
    @Schema(description = "用户ID")
    @NotNull(message = "用户ID不能为空")
    private Long id;

    /**
     * 真实姓名
     */
    @Schema(description = "真实姓名")
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

    /**
     * 状态
     */
    @Schema(description = "状态")
    private String status;
}
