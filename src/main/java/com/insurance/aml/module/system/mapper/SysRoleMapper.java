package com.insurance.aml.module.system.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.system.model.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统角色Mapper接口
 */
@Mapper
public interface SysRoleMapper extends BaseMapperX<SysRole> {

    /**
     * 根据用户ID查询角色编码列表
     */
    @Select("""
        SELECT DISTINCT r.role_code
        FROM t_role r
        INNER JOIN t_user_role ur ON ur.role_id = r.id
        WHERE ur.user_id = #{userId} AND r.status = 'ENABLED'
    """)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);
}
