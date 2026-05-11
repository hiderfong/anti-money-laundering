package com.insurance.aml.module.auth.service.impl;

import com.insurance.aml.module.auth.model.JwtUserDetails;
import com.insurance.aml.module.auth.model.LoginRequest;
import com.insurance.aml.module.auth.model.LoginResponse;
import com.insurance.aml.module.auth.model.UserProfileResponse;
import com.insurance.aml.module.auth.service.AuthTokenStore;
import com.insurance.aml.module.auth.service.JwtService;
import com.insurance.aml.module.system.mapper.SysPermissionMapper;
import com.insurance.aml.module.system.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.time.Duration;

/**
 * 认证服务实现
 * 处理登录、登出、刷新令牌等业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuthTokenStore authTokenStore;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应
     */
    public LoginResponse login(LoginRequest request) {
        log.info("用户登录: {}", request.getUsername());

        // 使用Spring Security认证管理器进行认证
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword()));

        // 获取认证后的用户信息
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();

        // 生成访问令牌和刷新令牌
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        // 将刷新令牌存储到令牌状态仓库，设置过期时间
        authTokenStore.saveRefreshToken(userDetails.getUserId(), refreshToken, Duration.ofDays(7));

        log.info("用户登录成功: userId={}, username={}", userDetails.getUserId(), userDetails.getUsername());

        // 查询用户角色和权限
        List<String> roles = resolveRoles(userDetails);
        List<String> permissions = resolvePermissions(userDetails.getUserId(), roles);

        // 构建登录响应
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpireSeconds())
                .userId(userDetails.getUserId())
                .username(userDetails.getUsername())
                .realName(userDetails.getRealName())
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    /**
     * 刷新访问令牌
     *
     * @param refreshToken 刷新令牌
     * @return 新的登录响应
     */
    public LoginResponse refreshToken(String refreshToken) {
        log.info("刷新访问令牌");

        // 验证刷新令牌是否有效
        if (!jwtService.validateToken(refreshToken)) {
            throw new RuntimeException("刷新令牌无效或已过期");
        }

        // 从刷新令牌中获取用户信息
        Long userId = jwtService.getUserIdFromToken(refreshToken);
        String username = jwtService.getUsernameFromToken(refreshToken);

        // 从令牌状态仓库中验证刷新令牌
        String storedRefreshToken = authTokenStore.getRefreshToken(userId);

        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new RuntimeException("刷新令牌无效，请重新登录");
        }

        // 加载用户信息
        JwtUserDetails userDetails = (JwtUserDetails) userDetailsService.loadUserByUsername(username);

        // 生成新的访问令牌
        String newAccessToken = jwtService.generateAccessToken(userDetails);
        // 生成新的刷新令牌
        String newRefreshToken = jwtService.generateRefreshToken(userDetails);

        // 更新令牌状态仓库中的刷新令牌
        authTokenStore.saveRefreshToken(userId, newRefreshToken, Duration.ofDays(7));

        log.info("访问令牌刷新成功: userId={}", userId);

        // 查询用户角色和权限
        List<String> roles = resolveRoles(userDetails);
        List<String> permissions = resolvePermissions(userId, roles);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpireSeconds())
                .userId(userDetails.getUserId())
                .username(userDetails.getUsername())
                .realName(userDetails.getRealName())
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    /**
     * 用户登出
     * 将当前访问令牌加入黑名单
     *
     * @param userDetails 当前用户信息
     */
    public void logout(JwtUserDetails userDetails) {
        logout(userDetails, null);
    }

    /**
     * 用户登出。
     *
     * @param userDetails 当前用户信息
     * @param accessToken 当前访问令牌，可为空
     */
    public void logout(JwtUserDetails userDetails, String accessToken) {
        log.info("用户登出: userId={}, username={}", userDetails.getUserId(), userDetails.getUsername());

        try {
            Duration accessTokenTtl = Duration.ofSeconds(jwtService.getAccessTokenExpireSeconds());
            if (accessToken != null && !accessToken.isBlank()) {
                authTokenStore.blacklistAccessToken(accessToken, accessTokenTtl);
            } else {
                authTokenStore.blacklistUserTokens(userDetails.getUserId(), accessTokenTtl);
            }

            // 清除刷新令牌
            authTokenStore.deleteRefreshToken(userDetails.getUserId());

            log.info("用户登出成功: userId={}", userDetails.getUserId());
        } catch (Exception e) {
            log.error("登出处理异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取当前登录用户资料。
     *
     * @param userDetails 当前认证用户
     * @return 当前用户资料
     */
    public UserProfileResponse getCurrentUser(JwtUserDetails userDetails) {
        List<String> roles = resolveRoles(userDetails);
        List<String> permissions = resolvePermissions(userDetails.getUserId(), roles);

        return UserProfileResponse.builder()
                .userId(userDetails.getUserId())
                .username(userDetails.getUsername())
                .realName(userDetails.getRealName())
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    private List<String> resolveRoles(JwtUserDetails userDetails) {
        List<String> roles = safeList(sysRoleMapper.findRoleCodesByUserId(userDetails.getUserId()));
        if (!roles.isEmpty()) {
            return distinct(roles);
        }

        if (userDetails.getAuthorities() == null) {
            return Collections.emptyList();
        }

        return distinct(userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith("ROLE_"))
                .toList());
    }

    private List<String> resolvePermissions(Long userId, List<String> roles) {
        if (roles.contains("ROLE_ADMIN")) {
            return distinct(safeList(sysPermissionMapper.findAllEnabledPermissionCodes()));
        }

        return distinct(safeList(sysPermissionMapper.findPermissionCodesByUserId(userId)));
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
