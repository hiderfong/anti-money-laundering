package com.insurance.aml.module.system.model.dto;

import com.insurance.aml.common.annotation.MaskField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户详情视图对象
 */
@Data
@Schema(description = "用户详情视图对象")
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @Schema(description = "用户ID")
    private Long id;

    /**
     * 用户名
     */
    @Schema(description = "用户名")
    private String username;

    /**
     * 真实姓名
     */
    @Schema(description = "真实姓名")
    private String realName;

    /**
     * 邮箱
     */
    @MaskField(MaskField.MaskType.EMAIL)
    @Schema(description = "邮箱")
    private String email;

    /**
     * 手机号码
     */
    @MaskField(MaskField.MaskType.PHONE)
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

    /**
     * 最后登录时间
     */
    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    /**
     * 关联角色名称列表
     */
    @Schema(description = "关联角色名称列表")
    private List<String> roles;
}
