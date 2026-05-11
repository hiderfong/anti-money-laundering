package com.insurance.aml.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.module.auth.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Security 安全配置
 * 配置JWT认证、CORS、权限控制等
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Value("${aml.cors.allowed-origins:#{null}}")
    private List<String> allowedOrigins;

    /**
     * 不需要认证的路径
     */
    private static final String[] PERMIT_ALL_PATHS = {
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/system/health",
            "/system/info",
            "/actuator/**",
            "/doc.html",
            "/webjars/**",
            "/swagger-resources/**",
            "/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF（REST API不需要）
                .csrf(AbstractHttpConfigurer::disable)
                // 配置CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 无状态会话管理
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 配置请求授权
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PERMIT_ALL_PATHS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                // 配置异常处理
                .exceptionHandling(exception -> exception
                        // 未认证处理：返回401 JSON
                        .authenticationEntryPoint(this::handleAuthenticationException)
                        // 无权限处理：返回403 JSON
                        .accessDeniedHandler(this::handleAccessDeniedException))
                // 添加JWT过滤器，在UsernamePasswordAuthenticationFilter之前
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * CORS跨域配置
     * dev/test环境: 允许所有来源(方便开发)
     * prod环境: 仅允许配置文件中指定的域名
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        if (isProdProfile()) {
            if (allowedOrigins == null || allowedOrigins.isEmpty()) {
                log.warn("生产环境未配置 CORS 允许的域名列表(aml.cors.allowed-origins)，默认拒绝所有跨域请求");
                configuration.setAllowedOrigins(List.of());
            } else {
                log.info("生产环境 CORS 允许的域名: {}", allowedOrigins);
                configuration.setAllowedOrigins(allowedOrigins);
            }
        } else {
            log.info("开发/测试环境 CORS: 允许所有来源");
            configuration.setAllowedOriginPatterns(List.of("*"));
        }

        // 允许的HTTP方法
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 允许的请求头
        configuration.setAllowedHeaders(List.of("*"));
        // 允许携带凭证
        configuration.setAllowCredentials(true);
        // 预检请求缓存时间
        configuration.setMaxAge(3600L);
        // 暴露的响应头
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private boolean isProdProfile() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    /**
     * 未认证异常处理 - 返回401
     */
    private void handleAuthenticationException(HttpServletRequest request,
                                               HttpServletResponse response,
                                               org.springframework.security.core.AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> result = new HashMap<>();
        result.put("code", 401);
        result.put("message", "未认证，请先登录");
        result.put("data", null);

        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    /**
     * 无权限异常处理 - 返回403
     */
    private void handleAccessDeniedException(HttpServletRequest request,
                                              HttpServletResponse response,
                                              org.springframework.security.access.AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> result = new HashMap<>();
        result.put("code", 403);
        result.put("message", "权限不足，无法访问该资源");
        result.put("data", null);

        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
