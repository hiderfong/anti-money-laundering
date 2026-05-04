package com.insurance.aml.module.system.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统权限实体
 */
@Data
@TableName("t_permission")
public class SysPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 权限编码 */
    private String permissionCode;

    /** 权限名称 */
    private String permissionName;

    /** 父权限ID */
    private Long parentId;

    /** 类型：MENU/BUTTON/API */
    private String type;

    /** 路由路径或API路径 */
    private String path;

    /** 排序号 */
    private Integer sortOrder;

    /** 图标 */
    private String icon;

    /** 状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdTime;
}
