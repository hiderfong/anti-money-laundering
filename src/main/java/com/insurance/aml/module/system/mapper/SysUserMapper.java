package com.insurance.aml.module.system.mapper;

import com.insurance.aml.common.mapper.BaseMapperX;
import com.insurance.aml.module.system.model.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统用户Mapper接口
 */
@Mapper
public interface SysUserMapper extends BaseMapperX<SysUser> {

    /**
     * 按角色编码查询启用用户ID。
     */
    @Select({
            "<script>",
            "SELECT DISTINCT u.id",
            "FROM t_user u",
            "INNER JOIN t_user_role ur ON ur.user_id = u.id",
            "INNER JOIN t_role r ON r.id = ur.role_id",
            "WHERE u.status = 'ENABLED'",
            "  AND r.status = 'ENABLED'",
            "  AND r.role_code IN",
            "<foreach collection='roleCodes' item='roleCode' open='(' separator=',' close=')'>",
            "  #{roleCode}",
            "</foreach>",
            "ORDER BY u.id ASC",
            "</script>"
    })
    List<Long> findEnabledUserIdsByRoleCodes(@Param("roleCodes") List<String> roleCodes);
}
