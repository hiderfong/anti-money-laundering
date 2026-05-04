package com.insurance.aml.module.auth.service;

import com.insurance.aml.module.auth.model.JwtUserDetails;
import com.insurance.aml.module.auth.model.LoginRequest;
import com.insurance.aml.module.auth.model.LoginResponse;
import com.insurance.aml.module.auth.service.impl.AuthServiceImpl;
import com.insurance.aml.module.auth.service.impl.UserDetailsServiceImpl;
import com.insurance.aml.module.system.mapper.SysPermissionMapper;
import com.insurance.aml.module.system.mapper.SysRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 认证服务单元测试
 * 覆盖 login / refreshToken / logout 核心方法
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("认证服务测试")
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SysRoleMapper sysRoleMapper;

    @Mock
    private SysPermissionMapper sysPermissionMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthServiceImpl authService;

    private JwtUserDetails testUserDetails;
    private LoginRequest loginRequest;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        testUserDetails = JwtUserDetails.builder()
                .userId(1L)
                .username("admin")
                .password("$2a$10$encoded_password")
                .realName("管理员")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .build();

        loginRequest = new LoginRequest();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("admin123");

        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(testUserDetails);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * 测试正确登录返回token+roles+permissions
     */
    @Test
    @DisplayName("正确登录 -> 返回accessToken、refreshToken、roles和permissions")
    void login_success_returnsTokenAndRoles() {
        // 准备
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtService.generateAccessToken(testUserDetails)).thenReturn("access_token_123");
        when(jwtService.generateRefreshToken(testUserDetails)).thenReturn("refresh_token_456");
        when(jwtService.getAccessTokenExpireSeconds()).thenReturn(3600L);
        when(sysRoleMapper.findRoleCodesByUserId(1L)).thenReturn(List.of("ROLE_ADMIN"));
        when(sysPermissionMapper.findAllEnabledPermissionCodes()).thenReturn(List.of("user:read", "user:write"));

        // 执行
        LoginResponse response = authService.login(loginRequest);

        // 验证
        assertNotNull(response, "登录响应不应为空");
        assertEquals("access_token_123", response.getAccessToken(), "accessToken应正确");
        assertEquals("refresh_token_456", response.getRefreshToken(), "refreshToken应正确");
        assertEquals("Bearer", response.getTokenType(), "tokenType应为Bearer");
        assertEquals(3600L, response.getExpiresIn(), "过期时间应正确");
        assertEquals(1L, response.getUserId(), "userId应正确");
        assertEquals("admin", response.getUsername(), "username应正确");
        assertEquals("管理员", response.getRealName(), "realName应正确");
        assertTrue(response.getRoles().contains("ROLE_ADMIN"), "角色列表应包含ROLE_ADMIN");
        assertFalse(response.getPermissions().isEmpty(), "权限列表不应为空");

        // 验证Redis存储刷新令牌
        verify(valueOperations).set(eq("jwt:refresh:1"), eq("refresh_token_456"), eq(7L), eq(TimeUnit.DAYS));
    }

    /**
     * 测试错误密码抛异常
     */
    @Test
    @DisplayName("错误密码 -> 抛出BadCredentialsException")
    void login_wrongPassword_throwsException() {
        // 准备
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("密码错误"));

        // 执行 & 验证
        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest),
                "错误密码应抛出BadCredentialsException");

        // 验证未生成token
        verify(jwtService, never()).generateAccessToken(any());
        verify(jwtService, never()).generateRefreshToken(any());
    }

    /**
     * 测试用户不存在抛异常
     */
    @Test
    @DisplayName("用户不存在 -> 抛出UsernameNotFoundException")
    void login_userNotFound_throwsException() {
        // 准备
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new UsernameNotFoundException("用户不存在: unknown"));

        LoginRequest unknownUserRequest = new LoginRequest();
        unknownUserRequest.setUsername("unknown");
        unknownUserRequest.setPassword("password");

        // 执行 & 验证
        assertThrows(UsernameNotFoundException.class, () -> authService.login(unknownUserRequest),
                "用户不存在应抛出UsernameNotFoundException");
    }

    /**
     * 测试token刷新成功
     */
    @Test
    @DisplayName("刷新令牌 -> 返回新的accessToken和refreshToken")
    void refreshToken_success_returnsNewTokens() {
        // 准备
        String oldRefreshToken = "old_refresh_token";
        when(jwtService.validateToken(oldRefreshToken)).thenReturn(true);
        when(jwtService.getUserIdFromToken(oldRefreshToken)).thenReturn(1L);
        when(jwtService.getUsernameFromToken(oldRefreshToken)).thenReturn("admin");
        when(valueOperations.get("jwt:refresh:1")).thenReturn(oldRefreshToken);
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(testUserDetails);
        when(jwtService.generateAccessToken(testUserDetails)).thenReturn("new_access_token");
        when(jwtService.generateRefreshToken(testUserDetails)).thenReturn("new_refresh_token");
        when(jwtService.getAccessTokenExpireSeconds()).thenReturn(3600L);
        when(sysRoleMapper.findRoleCodesByUserId(1L)).thenReturn(List.of("ROLE_ADMIN"));
        when(sysPermissionMapper.findAllEnabledPermissionCodes()).thenReturn(List.of("user:read"));

        // 执行
        LoginResponse response = authService.refreshToken(oldRefreshToken);

        // 验证
        assertNotNull(response, "刷新响应不应为空");
        assertEquals("new_access_token", response.getAccessToken(), "新accessToken应正确");
        assertEquals("new_refresh_token", response.getRefreshToken(), "新refreshToken应正确");
        assertEquals(1L, response.getUserId(), "userId应正确");

        // 验证Redis中的刷新令牌已更新
        verify(valueOperations).set(eq("jwt:refresh:1"), eq("new_refresh_token"), eq(7L), eq(TimeUnit.DAYS));
    }

    /**
     * 测试无效刷新令牌抛异常
     */
    @Test
    @DisplayName("无效刷新令牌 -> 抛出RuntimeException")
    void refreshToken_invalidToken_throwsException() {
        // 准备
        when(jwtService.validateToken("invalid_token")).thenReturn(false);

        // 执行 & 验证
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.refreshToken("invalid_token"));
        assertTrue(ex.getMessage().contains("刷新令牌无效"), "异常消息应提示令牌无效");
    }

    /**
     * 测试Redis中刷新令牌不匹配抛异常
     */
    @Test
    @DisplayName("Redis刷新令牌不匹配 -> 抛出RuntimeException")
    void refreshToken_mismatchInRedis_throwsException() {
        // 准备
        String refreshToken = "current_refresh_token";
        when(jwtService.validateToken(refreshToken)).thenReturn(true);
        when(jwtService.getUserIdFromToken(refreshToken)).thenReturn(1L);
        when(jwtService.getUsernameFromToken(refreshToken)).thenReturn("admin");
        when(valueOperations.get("jwt:refresh:1")).thenReturn("different_token");

        // 执行 & 验证
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.refreshToken(refreshToken));
        assertTrue(ex.getMessage().contains("刷新令牌无效"), "异常消息应提示令牌不匹配");
    }

    /**
     * 测试用户登出清除Redis令牌
     */
    @Test
    @DisplayName("用户登出 -> 将token加入黑名单并清除刷新令牌")
    void logout_success_clearsRedisTokens() {
        // 准备
        when(jwtService.getAccessTokenExpireSeconds()).thenReturn(3600L);

        // 执行
        authService.logout(testUserDetails);

        // 验证：将token加入黑名单
        verify(valueOperations).set(
                eq("jwt:blacklist:1"), eq("logout"), eq(3600L), eq(TimeUnit.SECONDS));

        // 验证：清除刷新令牌
        verify(redisTemplate).delete("jwt:refresh:1");
    }

    /**
     * 测试登出时Redis异常不影响主流程
     */
    @Test
    @DisplayName("登出时Redis异常 -> 不抛出异常")
    void logout_redisException_doesNotThrow() {
        // 准备：Redis操作抛异常
        when(jwtService.getAccessTokenExpireSeconds()).thenReturn(3600L);
        doThrow(new RuntimeException("Redis连接失败")).when(valueOperations)
                .set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        // 执行 & 验证：不应抛出异常（logout内部catch了异常）
        assertDoesNotThrow(() -> authService.logout(testUserDetails),
                "登出时Redis异常不应抛出");
    }

    /**
     * 测试ADMIN角色获取所有权限
     */
    @Test
    @DisplayName("ADMIN角色登录 -> 获取所有启用权限")
    void login_adminRole_getsAllPermissions() {
        // 准备
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtService.generateAccessToken(testUserDetails)).thenReturn("token");
        when(jwtService.generateRefreshToken(testUserDetails)).thenReturn("refresh");
        when(jwtService.getAccessTokenExpireSeconds()).thenReturn(3600L);
        when(sysRoleMapper.findRoleCodesByUserId(1L)).thenReturn(List.of("ROLE_ADMIN"));
        when(sysPermissionMapper.findAllEnabledPermissionCodes())
                .thenReturn(List.of("user:read", "user:write", "case:read", "alert:manage"));

        // 执行
        LoginResponse response = authService.login(loginRequest);

        // 验证：ADMIN角色应获取所有权限
        assertEquals(4, response.getPermissions().size(), "ADMIN应获取所有启用权限");
        verify(sysPermissionMapper).findAllEnabledPermissionCodes();
        verify(sysPermissionMapper, never()).findPermissionCodesByUserId(anyLong());
    }

    /**
     * 测试非ADMIN角色获取指定权限
     */
    @Test
    @DisplayName("非ADMIN角色登录 -> 按userId查询权限")
    void login_nonAdminRole_getsUserPermissions() {
        // 准备：非ADMIN用户
        JwtUserDetails operatorUser = JwtUserDetails.builder()
                .userId(2L)
                .username("operator")
                .password("$2a$10$encoded")
                .realName("操作员")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")))
                .build();

        Authentication operatorAuth = mock(Authentication.class);
        when(operatorAuth.getPrincipal()).thenReturn(operatorUser);

        LoginRequest operatorRequest = new LoginRequest();
        operatorRequest.setUsername("operator");
        operatorRequest.setPassword("pass123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(operatorAuth);
        when(jwtService.generateAccessToken(operatorUser)).thenReturn("op_token");
        when(jwtService.generateRefreshToken(operatorUser)).thenReturn("op_refresh");
        when(jwtService.getAccessTokenExpireSeconds()).thenReturn(3600L);
        when(sysRoleMapper.findRoleCodesByUserId(2L)).thenReturn(List.of("ROLE_OPERATOR"));
        when(sysPermissionMapper.findPermissionCodesByUserId(2L))
                .thenReturn(List.of("transaction:read", "alert:read"));

        // 执行
        LoginResponse response = authService.login(operatorRequest);

        // 验证：非ADMIN应按userId查询权限
        assertEquals(2, response.getPermissions().size(), "操作员应获取2个权限");
        verify(sysPermissionMapper).findPermissionCodesByUserId(2L);
        verify(sysPermissionMapper, never()).findAllEnabledPermissionCodes();
    }
}
