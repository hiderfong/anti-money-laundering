package com.insurance.aml.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insurance.aml.module.system.model.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限Mapper接口
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 根据用户ID查询权限编码列表
     * 通过 user -> role -> permission 三表关联查询
     */
    @Select("""
        SELECT DISTINCT p.permission_code
        FROM t_permission p
        INNER JOIN t_role_permission rp ON rp.permission_id = p.id
        INNER JOIN t_user_role ur ON ur.role_id = rp.role_id
        WHERE ur.user_id = #{userId} AND p.status = 'ENABLED'
    """)
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询全部启用权限编码。
     * 管理员角色拥有所有权限，登录响应需要返回完整权限集供前端做按钮级控制。
     */
    @Select("""
        SELECT DISTINCT p.permission_code
        FROM t_permission p
        WHERE p.status = 'ENABLED'
    """)
    List<String> findAllEnabledPermissionCodes();
}
