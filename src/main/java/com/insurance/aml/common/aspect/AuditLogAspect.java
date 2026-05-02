package com.insurance.aml.common.aspect;

import com.insurance.aml.common.annotation.AuditLog;
import com.insurance.aml.common.service.AuditLogService;
import com.insurance.aml.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 审计日志切面
 * 拦截标注了@AuditLog注解的方法，记录操作日志
 * 包含操作用户、请求地址、IP、方法参数、返回值、耗时等信息
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;

    private static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(?i)(password|token|secret|idNumber|phone|email)=([^,)}\\]]+)");

    /**
     * 环绕通知：记录审计日志
     */
    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取请求信息
        String uri = null;
        String ip = null;
        String requestMethod = null;
        String userAgent = null;
        String username = null;
        Long userId = null;
        try {
            username = SecurityUtils.getCurrentUsername();
            userId = SecurityUtils.getCurrentUserId();
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                uri = request.getRequestURI();
                ip = getClientIp(request);
                requestMethod = request.getMethod();
                userAgent = request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.warn("获取请求信息失败", e);
        }

        // 获取方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = signature.getDeclaringTypeName() + "." + method.getName();

        // 脱敏方法参数
        Object[] args = joinPoint.getArgs();
        String maskedArgs = maskSensitiveArgs(args);

        // 执行目标方法
        Object result = null;
        boolean success = true;
        String errorMsg = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable throwable) {
            success = false;
            errorMsg = throwable.getMessage();
            throw throwable;
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // 异步记录审计日志，确保不影响业务流程
            try {
                logAudit(auditLog, userId, username, uri, requestMethod, ip, userAgent, methodName, maskedArgs,
                        success, errorMsg, duration);
            } catch (Exception e) {
                log.error("记录审计日志失败，不影响业务流程", e);
            }
        }
    }

    /**
     * 异步记录审计日志
     */
    private void logAudit(AuditLog auditLog, Long userId, String username, String uri,
                          String requestMethod, String ip, String userAgent, String methodName, String args,
                          boolean success, String errorMsg, long duration) {
        String detail = String.format("description=%s, method=%s, args=%s",
                auditLog.description(), methodName, args);
        int responseCode = success ? 200 : 500;
        auditLogService.writeAuditLog(
                UUID.randomUUID().toString().replace("-", ""),
                userId,
                username,
                auditLog.operationType(),
                auditLog.module(),
                auditLog.module(),
                null,
                detail,
                ip,
                userAgent,
                uri,
                requestMethod,
                responseCode,
                duration,
                errorMsg
        );
        log.info("审计日志 - 模块: {}, 操作: {}, 描述: {}, 用户: {}, URI: {}, IP: {}, " +
                        "方法: {}, 参数: {}, 成功: {}, 耗时: {}ms",
                auditLog.module(), auditLog.operationType(), auditLog.description(),
                username, uri, ip, methodName, args, success, duration);
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 对敏感参数进行脱敏处理
     * 避免在日志中记录敏感信息
     */
    private String maskSensitiveArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        // 简单处理：对参数列表做基本脱敏
        return Arrays.toString(Arrays.stream(args)
                .map(arg -> {
                    String text = String.valueOf(arg);
                    text = SENSITIVE_VALUE_PATTERN.matcher(text).replaceAll("$1=***");
                    if (text.length() > 200) {
                        return text.substring(0, 200) + "...(已截断)";
                    }
                    return text;
                })
                .toArray());
    }
}
