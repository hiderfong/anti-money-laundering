package com.insurance.aml.module.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.insurance.aml.module.auth.model.JwtUserDetails;
import com.insurance.aml.module.system.mapper.SysPermissionMapper;
import com.insurance.aml.module.system.mapper.SysRoleMapper;
import com.insurance.aml.module.system.mapper.SysUserMapper;
import com.insurance.aml.module.system.model.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

/**
 * 用户认证服务实现
 * 从数据库加载用户信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_VIEWER = "ROLE_VIEWER";

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("加载用户信息: {}", username);

        // 从数据库查询用户
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        SysUser user = sysUserMapper.selectOne(wrapper);

        if (user == null) {
            log.warn("用户不存在: {}", username);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        if (!"ENABLED".equals(user.getStatus())) {
            log.warn("用户已被禁用: {}", username);
            throw new UsernameNotFoundException("用户已被禁用: " + username);
        }

        // 从数据库查询用户角色与按钮级权限，统一写入 Spring Security authorities。
        List<String> roleCodes = safeList(sysRoleMapper.findRoleCodesByUserId(user.getId()));
        List<String> permissionCodes = roleCodes.contains(ROLE_ADMIN)
                ? safeList(sysPermissionMapper.findAllEnabledPermissionCodes())
                : safeList(sysPermissionMapper.findPermissionCodesByUserId(user.getId()));

        List<SimpleGrantedAuthority> authorities = distinct(Stream.concat(roleCodes.stream(), permissionCodes.stream())
                        .toList()).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        // 如果没有分配角色，默认给一个空角色
        if (authorities.isEmpty()) {
            authorities = List.of(new SimpleGrantedAuthority(ROLE_VIEWER));
        }

        // 构建UserDetails，包含密码
        return JwtUserDetails.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .realName(user.getRealName())
                .authorities(authorities)
                .build();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private List<String> distinct(List<String> values) {
        return new LinkedHashSet<>(values).stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }
}
