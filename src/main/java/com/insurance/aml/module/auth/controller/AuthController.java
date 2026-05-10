package com.insurance.aml.module.auth.controller;

import com.insurance.aml.common.annotation.RateLimit;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.auth.model.JwtUserDetails;
import com.insurance.aml.module.auth.model.LoginRequest;
import com.insurance.aml.module.auth.model.LoginResponse;
import com.insurance.aml.module.auth.model.UserProfileResponse;
import com.insurance.aml.module.auth.service.impl.AuthServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 * 提供登录、登出、刷新令牌、获取当前用户信息等接口
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理")
public class AuthController {

    private final AuthServiceImpl authService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    @RateLimit(key = "login", maxRequests = 5, windowSeconds = 60, dimension = RateLimit.Dimension.IP, message = "登录尝试过于频繁")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    /**
     * 刷新访问令牌
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新访问令牌")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse response = authService.refreshToken(request.getRefreshToken());
        return Result.success(response);
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出")
    public Result<Void> logout(@AuthenticationPrincipal JwtUserDetails userDetails,
                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(userDetails, extractBearerToken(authorization));
        return Result.success();
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息")
    public Result<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal JwtUserDetails userDetails) {
        return Result.success(authService.getCurrentUser(userDetails));
    }

    /**
     * 刷新令牌请求参数
     */
    @Data
    public static class RefreshRequest {
        @jakarta.validation.constraints.NotBlank(message = "刷新令牌不能为空")
        @io.swagger.v3.oas.annotations.media.Schema(description = "刷新令牌")
        private String refreshToken;
    }

    private String extractBearerToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}
