package com.insurance.aml.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.module.system.mapper.SysRoleMapper;
import com.insurance.aml.module.system.mapper.SysUserMapper;
import com.insurance.aml.module.system.mapper.SysUserRoleMapper;
import com.insurance.aml.module.system.model.dto.*;
import com.insurance.aml.module.system.model.entity.SysRole;
import com.insurance.aml.module.system.model.entity.SysUser;
import com.insurance.aml.module.system.service.UserService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    private static final String USER_STATUS_ENABLED = "ENABLED";

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 创建用户：校验用户名唯一，BCrypt加密密码，保存用户，分配角色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO createUser(UserCreateRequest req) {
        // 校验用户名唯一性
        LambdaQueryWrapper<SysUser> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(SysUser::getUsername, req.getUsername());
        Long count = sysUserMapper.selectCount(checkWrapper);
        if (count > 0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        // 构建用户实体
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRealName(req.getRealName());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setDepartment(req.getDepartment());
        user.setPosition(req.getPosition());
        user.setStatus(USER_STATUS_ENABLED);
        user.setLoginFailCount(0);

        sysUserMapper.insert(user);
        log.info("创建用户成功，userId={}, username={}", user.getId(), user.getUsername());

        return convertToVO(user);
    }

    /**
     * 更新用户：仅更新非空字段
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUser(UserUpdateRequest req) {
        SysUser user = sysUserMapper.selectById(req.getId());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 仅更新非空字段
        if (StringUtils.hasText(req.getRealName())) {
            user.setRealName(req.getRealName());
        }
        if (StringUtils.hasText(req.getEmail())) {
            user.setEmail(req.getEmail());
        }
        if (StringUtils.hasText(req.getPhone())) {
            user.setPhone(req.getPhone());
        }
        if (StringUtils.hasText(req.getDepartment())) {
            user.setDepartment(req.getDepartment());
        }
        if (StringUtils.hasText(req.getPosition())) {
            user.setPosition(req.getPosition());
        }
        if (StringUtils.hasText(req.getStatus())) {
            user.setStatus(req.getStatus());
        }

        sysUserMapper.updateById(user);
        log.info("更新用户成功，userId={}", user.getId());

        return convertToVO(user);
    }

    /**
     * 删除用户：软删除，将状态置为DISABLED
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        user.setStatus("DISABLED");
        sysUserMapper.updateById(user);
        log.info("软删除用户成功，userId={}", id);
    }

    /**
     * 重置密码：BCrypt加密新密码后更新
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(user);
        log.info("重置用户密码成功，userId={}", id);
    }

    /**
     * 分页查询用户：支持用户名、姓名、部门、状态筛选，查询后加载角色信息
     */
    @Override
    public PageResult<UserVO> pageQueryUsers(UserQueryRequest req) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(req.getUsername())) {
            wrapper.like(SysUser::getUsername, req.getUsername());
        }
        if (StringUtils.hasText(req.getRealName())) {
            wrapper.like(SysUser::getRealName, req.getRealName());
        }
        if (StringUtils.hasText(req.getDepartment())) {
            wrapper.eq(SysUser::getDepartment, req.getDepartment());
        }
        if (StringUtils.hasText(req.getStatus())) {
            wrapper.eq(SysUser::getStatus, req.getStatus());
        }

        wrapper.orderByDesc(SysUser::getCreatedTime);

        IPage<SysUser> page = sysUserMapper.selectPage(req.toPage(), wrapper);

        // 将实体列表转换为VO列表，并为每个用户加载角色名称
        List<UserVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        PageResult<UserVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPage((int) page.getCurrent());
        result.setSize((int) page.getSize());
        result.setList(voList);
        return result;
    }

    /**
     * 获取用户详情：加载用户基本信息和关联角色
     */
    @Override
    public UserVO getUserDetail(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return convertToVO(user);
    }

    /**
     * 分配角色：先删除旧关联，再插入新关联
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        // 校验用户是否存在
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 删除旧的用户-角色关联
        sysUserRoleMapper.deleteByUserId(userId);

        // 插入新的用户-角色关联
        if (roleIds != null && !roleIds.isEmpty()) {
            sysUserRoleMapper.insertBatch(userId, roleIds);
        }

        log.info("用户角色分配成功，userId={}, roleIds={}", userId, roleIds);
    }

    /**
     * 将SysUser实体转换为UserVO，同时加载关联角色名称
     */
    private UserVO convertToVO(SysUser user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);

        // 加载角色名称列表
        List<Long> roleIds = sysUserRoleMapper.findRoleIdsByUserId(user.getId());
        if (roleIds != null && !roleIds.isEmpty()) {
            List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
            List<String> roleNames = roles.stream()
                    .map(SysRole::getRoleName)
                    .collect(Collectors.toList());
            vo.setRoles(roleNames);
        } else {
            vo.setRoles(Collections.emptyList());
        }

        return vo;
    }
}
