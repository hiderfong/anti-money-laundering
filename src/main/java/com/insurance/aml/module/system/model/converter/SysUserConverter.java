package com.insurance.aml.module.system.model.converter;

import com.insurance.aml.module.system.model.dto.UserVO;
import com.insurance.aml.module.system.model.entity.SysUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * SysUser 对象转换器
 * 使用 MapStruct 替代 BeanUtils.copyProperties
 *
 * SysUser 实体中 passwordHash 等敏感字段不映射到 UserVO
 * SysUser 中的 lastLoginIp/passwordChangedTime/loginFailCount/remark/createdBy/updatedBy 等
 * 在 UserVO 中无对应字段，自动忽略
 */
@Mapper(componentModel = "spring")
public interface SysUserConverter {

    /**
     * SysUser 实体 -> UserVO
     * 自动忽略 SysUser 中有但 UserVO 中没有的字段（如 passwordHash、lastLoginIp 等）
     * UserVO 中的 roles 字段需在 Service 层手动设置
     */
    @Mapping(target = "roles", ignore = true)
    UserVO toVO(SysUser sysUser);

    /**
     * SysUser 实体列表 -> UserVO 列表
     */
    List<UserVO> toVOList(List<SysUser> sysUsers);

    /**
     * UserVO -> SysUser 实体（反向转换，注意不会设置 passwordHash）
     */
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "passwordChangedTime", ignore = true)
    @Mapping(target = "loginFailCount", ignore = true)
    SysUser toEntity(UserVO userVO);
}
