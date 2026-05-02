package com.insurance.aml.module.auth.filter;

import com.insurance.aml.module.auth.model.JwtUserDetails;
import com.insurance.aml.module.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JWT认证过滤器
 * 从请求头中提取Bearer令牌，验证并设置安全上下文
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 不需要JWT认证的路径
     */
    private static final String[] SKIP_PATHS = {
            "/auth/login",
            "/auth/register",
            "/doc.html",
            "/webjars/**",
            "/swagger-resources/**",
            "/v3/api-docs/**"
    };

    /**
     * Redis中JWT黑名单的前缀
     */
    private static final String JWT_BLACKLIST_PREFIX = "jwt:blacklist:";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 跳过不需要认证的路径
        if (shouldSkip(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 从请求头中提取令牌
            String token = extractToken(request);

            if (StringUtils.hasText(token)) {
                // 验证令牌是否在黑名单中
                if (isTokenBlacklisted(token)) {
                    log.warn("JWT令牌已在黑名单中: {}", path);
                    filterChain.doFilter(request, response);
                    return;
                }

                // 验证令牌有效性
                if (jwtService.validateToken(token)) {
                    // 解析令牌获取用户信息
                    Claims claims = jwtService.parseToken(token);
                    String username = claims.getSubject();
                    Long userId = claims.get("userId", Long.class);
                    String rolesStr = claims.get("roles", String.class);

                    // 构建权限列表
                    List<SimpleGrantedAuthority> authorities = Arrays.stream(
                                    rolesStr != null ? rolesStr.split(",") : new String[0])
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    // 创建用户详情对象
                    JwtUserDetails userDetails = JwtUserDetails.builder()
                            .userId(userId)
                            .username(username)
                            .authorities(authorities)
                            .build();

                    // 创建认证令牌并设置到安全上下文
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("JWT认证成功: userId={}, username={}", userId, username);
                } else {
                    log.warn("JWT令牌验证失败: {}", path);
                }
            }
        } catch (Exception e) {
            log.error("JWT认证处理异常: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从Authorization头中提取Bearer令牌
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 判断路径是否需要跳过
     */
    private boolean shouldSkip(String path) {
        for (String pattern : SKIP_PATHS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查令牌是否在黑名单中（已登出）
     */
    private boolean isTokenBlacklisted(String token) {
        try {
            String blacklistKey = JWT_BLACKLIST_PREFIX + token;
            return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey));
        } catch (Exception e) {
            // Redis不可用时不阻塞认证流程
            log.warn("检查JWT黑名单失败（Redis不可用）: {}", e.getMessage());
            return false;
        }
    }
}
