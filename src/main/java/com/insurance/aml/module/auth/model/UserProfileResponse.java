package com.insurance.aml.module.auth.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 当前登录用户资料响应。
 */
@Data
@Builder
@Schema(description = "当前登录用户资料")
public class UserProfileResponse {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "用户角色列表")
    private List<String> roles;

    @Schema(description = "用户权限编码列表")
    private List<String> permissions;
}
