package com.insurance.aml.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 角色创建请求
 */
@Data
@Schema(description = "角色创建请求参数")
public class RoleCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色编码（必填）
     */
    @Schema(description = "角色编码")
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;

    /**
     * 角色名称（必填）
     */
    @Schema(description = "角色名称")
    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    /**
     * 角色描述
     */
    @Schema(description = "角色描述")
    private String description;

    /**
     * 关联权限ID列表
     */
    @Schema(description = "关联权限ID列表")
    private List<Long> permissionIds;
}
