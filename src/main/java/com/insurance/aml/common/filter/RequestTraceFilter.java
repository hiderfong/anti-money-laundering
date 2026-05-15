package com.insurance.aml.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求追踪过滤器。
 * 为每次请求设置 traceId，并通过 X-Request-Id 响应头回传，便于日志、审计和客户端问题定位串联。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final int MAX_REQUEST_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(REQUEST_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (!StringUtils.hasText(requestId)) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        String sanitized = requestId.trim();
        if (sanitized.length() > MAX_REQUEST_ID_LENGTH) {
            sanitized = sanitized.substring(0, MAX_REQUEST_ID_LENGTH);
        }
        return sanitized.replaceAll("[^a-zA-Z0-9._:-]", "_");
    }
}
