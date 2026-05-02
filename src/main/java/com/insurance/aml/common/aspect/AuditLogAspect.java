package com.insurance.aml.common.aspect;

import com.insurance.aml.common.annotation.AuditLog;
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

    // private final AuditLogService auditLogService;

    /**
     * 环绕通知：记录审计日志
     */
    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取请求信息
        String uri = null;
        String ip = null;
        String username = null;
        try {
            username = SecurityUtils.getCurrentUsername();
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                uri = request.getRequestURI();
                ip = getClientIp(request);
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
                logAudit(auditLog, username, uri, ip, methodName, maskedArgs,
                        success, errorMsg, duration);
            } catch (Exception e) {
                log.error("记录审计日志失败，不影响业务流程", e);
            }
        }
    }

    /**
     * 异步记录审计日志
     */
    private void logAudit(AuditLog auditLog, String username, String uri,
                          String ip, String methodName, String args,
                          boolean success, String errorMsg, long duration) {
        // TODO: 调用AuditLogService异步保存审计日志
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
                    if (arg instanceof String str) {
                        // 对可能是敏感信息的字符串做长度截断
                        if (str.length() > 50) {
                            return str.substring(0, 20) + "...(已截断)";
                        }
                    }
                    return arg;
                })
                .toArray());
    }
}
