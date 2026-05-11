package com.insurance.aml.module.auth.service;

import com.insurance.aml.module.auth.model.JwtUserDetails;
import com.insurance.aml.module.auth.service.impl.UserDetailsServiceImpl;
import com.insurance.aml.module.system.mapper.SysPermissionMapper;
import com.insurance.aml.module.system.mapper.SysRoleMapper;
import com.insurance.aml.module.system.mapper.SysUserMapper;
import com.insurance.aml.module.system.model.entity.SysUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 用户认证信息加载测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户认证信息加载测试")
class UserDetailsServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysRoleMapper sysRoleMapper;

    @Mock
    private SysPermissionMapper sysPermissionMapper;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    @DisplayName("普通角色登录 -> authorities 同时包含角色和按钮权限")
    void loadUserByUsername_regularRole_includesRolesAndPermissions() {
        SysUser user = enabledUser(2L, "investigator");
        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(sysRoleMapper.findRoleCodesByUserId(2L)).thenReturn(List.of("ROLE_INVESTIGATOR"));
        when(sysPermissionMapper.findPermissionCodesByUserId(2L))
                .thenReturn(List.of("customer:create", "case:create"));

        JwtUserDetails details = (JwtUserDetails) userDetailsService.loadUserByUsername("investigator");

        Set<String> authorities = authoritySet(details);
        assertTrue(authorities.contains("ROLE_INVESTIGATOR"));
        assertTrue(authorities.contains("customer:create"));
        assertTrue(authorities.contains("case:create"));
        verify(sysPermissionMapper, never()).findAllEnabledPermissionCodes();
    }

    @Test
    @DisplayName("管理员登录 -> authorities 包含全部启用权限")
    void loadUserByUsername_adminRole_includesAllPermissions() {
        SysUser user = enabledUser(1L, "admin");
        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(sysRoleMapper.findRoleCodesByUserId(1L)).thenReturn(List.of("ROLE_ADMIN"));
        when(sysPermissionMapper.findAllEnabledPermissionCodes())
                .thenReturn(List.of("system:user", "customer:create"));

        JwtUserDetails details = (JwtUserDetails) userDetailsService.loadUserByUsername("admin");

        Set<String> authorities = authoritySet(details);
        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertTrue(authorities.contains("system:user"));
        assertTrue(authorities.contains("customer:create"));
        verify(sysPermissionMapper, never()).findPermissionCodesByUserId(1L);
    }

    @Test
    @DisplayName("新建用户未分配角色 -> 默认 ROLE_VIEWER 且可登录")
    void loadUserByUsername_noRole_defaultsToViewer() {
        SysUser user = enabledUser(3L, "new_user");
        when(sysUserMapper.selectOne(any())).thenReturn(user);
        when(sysRoleMapper.findRoleCodesByUserId(3L)).thenReturn(List.of());
        when(sysPermissionMapper.findPermissionCodesByUserId(3L)).thenReturn(List.of());

        JwtUserDetails details = (JwtUserDetails) userDetailsService.loadUserByUsername("new_user");

        Set<String> authorities = authoritySet(details);
        assertEquals(Set.of("ROLE_VIEWER"), authorities);
    }

    @Test
    @DisplayName("非 ENABLED 状态用户 -> 拒绝登录")
    void loadUserByUsername_disabledUser_throws() {
        SysUser user = enabledUser(4L, "disabled_user");
        user.setStatus("ACTIVE");
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("disabled_user"));
    }

    private SysUser enabledUser(Long id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("$2a$10$encoded");
        user.setRealName(username);
        user.setStatus("ENABLED");
        return user;
    }

    private Set<String> authoritySet(JwtUserDetails details) {
        return details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
