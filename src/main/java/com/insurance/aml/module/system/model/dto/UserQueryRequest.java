package com.insurance.aml.module.system.model.dto;

import com.insurance.aml.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户查询请求参数")
public class UserQueryRequest extends PageQuery {

    /**
     * 用户名（模糊查询）
     */
    @Schema(description = "用户名，模糊查询")
    private String username;

    /**
     * 真实姓名（模糊查询）
     */
    @Schema(description = "真实姓名，模糊查询")
    private String realName;

    /**
     * 部门
     */
    @Schema(description = "部门")
    private String department;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private String status;
}
