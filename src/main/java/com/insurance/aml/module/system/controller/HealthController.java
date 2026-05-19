package com.insurance.aml.module.system.controller;

import com.insurance.aml.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统管理控制器
 * 提供健康检查和系统信息接口
 */
@Slf4j
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
@Tag(name = "系统管理", description = "系统管理相关接口")
public class HealthController {
    private final DataSource dataSource;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;
    @Value("${spring.application.name:aml-system}")
    private String appName;
    @Value("${app.version:1.0.0}")
    private String appVersion;

    /**
     * 系统健康检查
     * 检查数据库和Redis的连通性
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查数据库和Redis的连通性，返回系统健康状态")
    public Result<Map<String, Object>> health() {
        log.debug("接收到健康检查请求");

        Map<String, Object> healthInfo = new LinkedHashMap<>();
        boolean allHealthy = true;

        // 检查数据库连通性
        try (Connection connection = dataSource.getConnection()) {
            connection.isValid(2);
            healthInfo.put("database", "UP");
        } catch (Exception e) {
            log.error("数据库健康检查失败：{}", e.getMessage());
            healthInfo.put("database", "DOWN");
            healthInfo.put("databaseError", e.getMessage());
            allHealthy = false;
        }

        // 检查Redis连通性。no-redis 本地验收环境下，Redis 为显式禁用。
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (stringRedisTemplate == null) {
            healthInfo.put("redis", "DISABLED");
        } else {
            try {
                String pong = stringRedisTemplate.getConnectionFactory().getConnection().ping();
                healthInfo.put("redis", "UP");
                healthInfo.put("redisPing", pong);
            } catch (Exception e) {
                log.error("Redis健康检查失败：{}", e.getMessage());
                healthInfo.put("redis", "DOWN");
                healthInfo.put("redisError", e.getMessage());
                allHealthy = false;
            }
        }

        healthInfo.put("status", allHealthy ? "UP" : "DEGRADED");

        if (allHealthy) {
            return Result.success(healthInfo);
        } else {
            return Result.fail(503, "部分服务不可用");
        }
    }

    /**
     * 系统信息
     * 返回应用版本、Java版本、运行时间等信息
     */
    @GetMapping("/info")
    @Operation(summary = "系统信息", description = "返回应用版本、Java版本、运行时间等系统信息")
    public Result<Map<String, Object>> info() {
        log.debug("接收到系统信息请求");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("appName", appName);
        info.put("appVersion", appVersion);
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("javaVendor", System.getProperty("java.vendor"));
        info.put("osName", System.getProperty("os.name"));
        info.put("osArch", System.getProperty("os.arch"));

        // 运行时间
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long uptimeSeconds = uptimeMs / 1000;
        long hours = uptimeSeconds / 3600;
        long minutes = (uptimeSeconds % 3600) / 60;
        long seconds = uptimeSeconds % 60;
        info.put("uptime", String.format("%d小时%d分%d秒", hours, minutes, seconds));
        info.put("uptimeMs", uptimeMs);

        // JVM信息
        Runtime runtime = Runtime.getRuntime();
        info.put("jvmMaxMemory", formatMemory(runtime.maxMemory()));
        info.put("jvmTotalMemory", formatMemory(runtime.totalMemory()));
        info.put("jvmFreeMemory", formatMemory(runtime.freeMemory()));
        info.put("jvmUsedMemory", formatMemory(runtime.totalMemory() - runtime.freeMemory()));
        info.put("availableProcessors", runtime.availableProcessors());

        return Result.success(info);
    }

    /**
     * 格式化内存大小为可读字符串
     */
    private String formatMemory(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f KB", bytes / 1024.0);
        }
    }
}
