package com.insurance.aml.common.aspect;

import com.insurance.aml.common.annotation.RateLimit;
import com.insurance.aml.common.exception.RateLimitException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 接口限流切面
 *
 * 基于 Redis + Lua 滑动窗口算法
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final HttpServletRequest request;
    private final Map<String, Deque<Long>> localWindows = new ConcurrentHashMap<>();

    private DefaultRedisScript<List> rateLimitScript;

    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/rate_limit.lua")));
        rateLimitScript.setResultType(List.class);
    }

    @Before("@annotation(com.insurance.aml.common.annotation.RateLimit)")
    public void doBefore(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        if (rateLimit == null) {
            return;
        }

        String limitKey = buildLimitKey(rateLimit, method);
        long windowMs = rateLimit.windowSeconds() * 1000L;
        int maxRequests = rateLimit.maxRequests();
        long now = System.currentTimeMillis();

        List<Long> result = executeRateLimit(limitKey, windowMs, maxRequests, now);

        if (result != null && !result.isEmpty()) {
            long allowed = result.get(0);
            long remaining = result.get(1);
            long retryAfter = result.get(2);

            // 设置响应头
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null && attrs.getResponse() != null) {
                attrs.getResponse().setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
                attrs.getResponse().setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
                attrs.getResponse().setHeader("X-RateLimit-Reset", String.valueOf((now + retryAfter) / 1000));
            }

            if (allowed == 0) {
                log.warn("接口限流触发: key={}, ip={}, uri={}", limitKey, getClientIp(), request.getRequestURI());
                throw new RateLimitException(
                        rateLimit.message() + "（" + (retryAfter / 1000 + 1) + "秒后重试）",
                        retryAfter / 1000 + 1
                );
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> executeRateLimit(String limitKey, long windowMs, int maxRequests, long now) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                return redisTemplate.execute(
                        rateLimitScript,
                        Collections.singletonList(limitKey),
                        String.valueOf(windowMs),
                        String.valueOf(maxRequests),
                        String.valueOf(now)
                );
            } catch (Exception e) {
                log.warn("Redis限流执行失败，降级为本地内存限流: key={}, error={}", limitKey, e.getMessage());
            }
        }
        return executeLocalRateLimit(limitKey, windowMs, maxRequests, now);
    }

    private List<Long> executeLocalRateLimit(String limitKey, long windowMs, int maxRequests, long now) {
        Deque<Long> timestamps = localWindows.computeIfAbsent(limitKey, ignored -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            long windowStart = now - windowMs;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.removeFirst();
            }

            if (timestamps.size() >= maxRequests) {
                Long first = timestamps.peekFirst();
                long retryAfter = first == null ? windowMs : Math.max(0, first + windowMs - now);
                return List.of(0L, 0L, retryAfter);
            }

            timestamps.addLast(now);
            long remaining = Math.max(0, maxRequests - timestamps.size());
            return List.of(1L, remaining, windowMs);
        }
    }

    /**
     * 构建限流 key
     */
    private String buildLimitKey(RateLimit rateLimit, Method method) {
        String prefix = rateLimit.key().isEmpty()
                ? method.getDeclaringClass().getSimpleName() + "." + method.getName()
                : rateLimit.key();

        return switch (rateLimit.dimension()) {
            case IP -> "rate_limit:ip:" + getClientIp() + ":" + prefix;
            case USER -> {
                String userId = request.getHeader("X-User-Id");
                if (userId == null || userId.isEmpty()) {
                    userId = getClientIp();
                }
                yield "rate_limit:user:" + userId + ":" + prefix;
            }
            case GLOBAL -> "rate_limit:global:" + prefix;
        };
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
