package com.insurance.aml.module.auth.service;

import com.insurance.aml.module.auth.model.JwtUserDetails;
import com.insurance.aml.module.auth.model.LoginRequest;
import com.insurance.aml.module.auth.model.LoginResponse;
import com.insurance.aml.module.auth.service.AuthTokenStore;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

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
    private AuthTokenStore authTokenStore;

    @Mock
    private SysRoleMapper sysRoleMapper;

    @Mock
    private SysPermissionMapper sysPermissionMapper;

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
        loginRequest.setPassword("Aml@Admin#2026!");

        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(testUserDetails);

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

        // 验证存储刷新令牌
        verify(authTokenStore).saveRefreshToken(1L, "refresh_token_456", Duration.ofDays(7));
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
        when(authTokenStore.getRefreshToken(1L)).thenReturn(oldRefreshToken);
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

        // 验证刷新令牌已更新
        verify(authTokenStore).saveRefreshToken(1L, "new_refresh_token", Duration.ofDays(7));
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
     * 测试令牌仓库中刷新令牌不匹配抛异常
     */
    @Test
    @DisplayName("刷新令牌仓库不匹配 -> 抛出RuntimeException")
    void refreshToken_mismatchInRedis_throwsException() {
        // 准备
        String refreshToken = "current_refresh_token";
        when(jwtService.validateToken(refreshToken)).thenReturn(true);
        when(jwtService.getUserIdFromToken(refreshToken)).thenReturn(1L);
        when(jwtService.getUsernameFromToken(refreshToken)).thenReturn("admin");
        when(authTokenStore.getRefreshToken(1L)).thenReturn("different_token");

        // 执行 & 验证
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.refreshToken(refreshToken));
        assertTrue(ex.getMessage().contains("刷新令牌无效"), "异常消息应提示令牌不匹配");
    }

    /**
     * 测试用户登出清除令牌
     */
    @Test
    @DisplayName("用户登出 -> 将token加入黑名单并清除刷新令牌")
    void logout_success_clearsRedisTokens() {
        // 准备
        when(jwtService.getAccessTokenExpireSeconds()).thenReturn(3600L);

        // 执行
        authService.logout(testUserDetails);

        // 验证：将当前用户令牌加入黑名单
        verify(authTokenStore).blacklistUserTokens(1L, Duration.ofSeconds(3600));

        // 验证：清除刷新令牌
        verify(authTokenStore).deleteRefreshToken(1L);
    }

    /**
     * 测试登出时令牌仓库异常不影响主流程
     */
    @Test
    @DisplayName("登出时令牌仓库异常 -> 不抛出异常")
    void logout_redisException_doesNotThrow() {
        // 准备：令牌仓库操作抛异常
        when(jwtService.getAccessTokenExpireSeconds()).thenReturn(3600L);
        doThrow(new RuntimeException("令牌仓库不可用")).when(authTokenStore)
                .blacklistUserTokens(anyLong(), any(Duration.class));

        // 执行 & 验证：不应抛出异常（logout内部catch了异常）
        assertDoesNotThrow(() -> authService.logout(testUserDetails),
                "登出时令牌仓库异常不应抛出");
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

    // ==================== 新增测试用例 ====================

    /**
     * 测试锁定账号登录抛出DisabledException
     */
    @Test
    @DisplayName("锁定账号登录 -> 抛出DisabledException")
    void testLoginWithLockedAccount() {
        // 准备：认证管理器抛出DisabledException（模拟锁定账号）
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new org.springframework.security.authentication.DisabledException("账户已被锁定"));

        // 执行 & 验证
        assertThrows(org.springframework.security.authentication.DisabledException.class,
                () -> authService.login(loginRequest),
                "锁定账号应抛出DisabledException");

        // 验证：未生成任何token
        verify(jwtService, never()).generateAccessToken(any());
        verify(jwtService, never()).generateRefreshToken(any());
        // 验证：未存储token
        verify(authTokenStore, never()).saveRefreshToken(anyLong(), anyString(), any(Duration.class));
    }

    /**
     * 测试错误密码登录（不同密码场景）
     */
    @Test
    @DisplayName("错误密码登录 -> 验证不生成token且不存储令牌")
    void testLoginWithWrongPassword() {
        // 准备
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("密码错误"));

        LoginRequest wrongPassRequest = new LoginRequest();
        wrongPassRequest.setUsername("admin");
        wrongPassRequest.setPassword("wrong_password_123");

        // 执行 & 验证
        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authService.login(wrongPassRequest));
        assertEquals("密码错误", ex.getMessage());

        // 验证：未生成token
        verify(jwtService, never()).generateAccessToken(any());
        verify(jwtService, never()).generateRefreshToken(any());
        // 验证：未存储任何令牌数据
        verifyNoInteractions(authTokenStore);
    }

    /**
     * 测试过期token刷新（validateToken返回false模拟过期）
     */
    @Test
    @DisplayName("过期token刷新 -> 抛出RuntimeException提示令牌无效或已过期")
    void testRefreshTokenWithExpiredToken() {
        // 准备：validateToken对过期token返回false
        String expiredToken = "eyJhbGciOiJIUzI1NiJ9.expired.token";
        when(jwtService.validateToken(expiredToken)).thenReturn(false);

        // 执行 & 验证
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.refreshToken(expiredToken));
        assertTrue(ex.getMessage().contains("刷新令牌无效"), "过期token应提示令牌无效或已过期");

        // 验证：未从令牌仓库获取数据
        verify(authTokenStore, never()).getRefreshToken(anyLong());
        // 验证：未生成新token
        verify(jwtService, never()).generateAccessToken(any());
        verify(jwtService, never()).generateRefreshToken(any());
    }

    /**
     * 测试无效token格式刷新
     */
    @Test
    @DisplayName("无效token格式刷新 -> 抛出RuntimeException")
    void testRefreshTokenWithInvalidToken() {
        // 准备：格式错误的token
        String malformedToken = "not-a-valid-jwt-token-format";
        when(jwtService.validateToken(malformedToken)).thenReturn(false);

        // 执行 & 验证
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.refreshToken(malformedToken));
        assertTrue(ex.getMessage().contains("刷新令牌无效"), "无效格式token应提示令牌无效");

        // 验证：未查询令牌仓库
        verify(authTokenStore, never()).getRefreshToken(anyLong());
        // 验证：未重新加载用户
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    /**
     * 测试用户登出完整流程
     */
    @Test
    @DisplayName("用户登出 -> 将token加入黑名单并删除刷新令牌")
    void testLogout() {
        // 准备
        when(jwtService.getAccessTokenExpireSeconds()).thenReturn(7200L);

        // 执行
        authService.logout(testUserDetails);

        // 验证：将当前用户token加入黑名单
        verify(authTokenStore).blacklistUserTokens(1L, Duration.ofSeconds(7200));

        // 验证：清除刷新令牌
        verify(authTokenStore).deleteRefreshToken(1L);

        // 验证：令牌仓库操作只执行了两次（一次黑名单，一次删除刷新令牌）
        verify(authTokenStore, times(1)).blacklistUserTokens(anyLong(), any(Duration.class));
        verify(authTokenStore, times(1)).deleteRefreshToken(anyLong());
    }

    /**
     * 测试获取当前用户信息（验证登录响应包含完整用户信息）
     */
    @Test
    @DisplayName("获取当前用户信息 -> 登录响应包含userId、username、realName等完整信息")
    void testGetCurrentUserInfo() {
        // 准备
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtService.generateAccessToken(testUserDetails)).thenReturn("access_token_info");
        when(jwtService.generateRefreshToken(testUserDetails)).thenReturn("refresh_token_info");
        when(jwtService.getAccessTokenExpireSeconds()).thenReturn(3600L);
        when(sysRoleMapper.findRoleCodesByUserId(1L)).thenReturn(List.of("ROLE_ADMIN", "ROLE_AUDITOR"));
        when(sysPermissionMapper.findAllEnabledPermissionCodes())
                .thenReturn(List.of("user:read", "user:write", "alert:manage", "case:read"));

        // 执行
        LoginResponse response = authService.login(loginRequest);

        // 验证：用户基本信息完整
        assertNotNull(response, "响应不应为空");
        assertEquals(1L, response.getUserId(), "userId应正确");
        assertEquals("admin", response.getUsername(), "username应正确");
        assertEquals("管理员", response.getRealName(), "realName应正确");

        // 验证：角色信息正确
        assertNotNull(response.getRoles(), "角色列表不应为空");
        assertTrue(response.getRoles().contains("ROLE_ADMIN"), "应包含ROLE_ADMIN");
        assertTrue(response.getRoles().contains("ROLE_AUDITOR"), "应包含ROLE_AUDITOR");
        assertEquals(2, response.getRoles().size(), "应有2个角色");

        // 验证：权限信息正确
        assertNotNull(response.getPermissions(), "权限列表不应为空");
        assertEquals(4, response.getPermissions().size(), "应有4个权限");
        assertTrue(response.getPermissions().contains("user:read"), "应包含user:read");
        assertTrue(response.getPermissions().contains("alert:manage"), "应包含alert:manage");

        // 验证：token信息完整
        assertNotNull(response.getAccessToken(), "accessToken不应为空");
        assertNotNull(response.getRefreshToken(), "refreshToken不应为空");
        assertEquals("Bearer", response.getTokenType(), "tokenType应为Bearer");
        assertEquals(3600L, response.getExpiresIn(), "过期时间应正确");
    }
}
