package com.insurance.aml.module.system.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户-角色关联Mapper接口
 */
@Mapper
public interface SysUserRoleMapper {

    /**
     * 根据用户ID查询角色ID列表
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    @Select("SELECT role_id FROM t_user_role WHERE user_id = #{userId}")
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID删除所有关联关系
     *
     * @param userId 用户ID
     */
    @Delete("DELETE FROM t_user_role WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * 批量插入用户-角色关联关系
     *
     * @param userId  用户ID
     * @param roleIds 角色ID列表
     */
    @Insert({
            "<script>",
            "INSERT INTO t_user_role (user_id, role_id) VALUES ",
            "<foreach collection='roleIds' item='roleId' separator=','>",
            "(#{userId}, #{roleId})",
            "</foreach>",
            "</script>"
    })
    void insertBatch(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);
}
