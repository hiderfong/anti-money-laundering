package com.insurance.aml.module.system.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 角色-权限关联Mapper接口
 */
@Mapper
public interface SysRolePermissionMapper {

    /**
     * 根据角色ID查询权限ID列表
     *
     * @param roleId 角色ID
     * @return 权限ID列表
     */
    @Select("SELECT permission_id FROM t_role_permission WHERE role_id = #{roleId}")
    List<Long> findPermissionIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 根据角色ID删除所有关联关系
     *
     * @param roleId 角色ID
     */
    @Delete("DELETE FROM t_role_permission WHERE role_id = #{roleId}")
    void deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量插入角色-权限关联关系
     *
     * @param roleId        角色ID
     * @param permissionIds 权限ID列表
     */
    @Insert({
            "<script>",
            "INSERT INTO t_role_permission (role_id, permission_id) VALUES ",
            "<foreach collection='permissionIds' item='permissionId' separator=','>",
            "(#{roleId}, #{permissionId})",
            "</foreach>",
            "</script>"
    })
    void insertBatch(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);
}
