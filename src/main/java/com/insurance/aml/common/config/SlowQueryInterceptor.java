package com.insurance.aml.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * 慢查询拦截器
 * 拦截 MyBatis SQL 执行，记录执行时间超过阈值的慢查询
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class SlowQueryInterceptor implements Interceptor {

    /**
     * 慢查询阈值，默认 500ms
     */
    @Value("${aml.slow-query.threshold:500}")
    private long slowQueryThreshold;

    /**
     * 是否启用慢查询日志，默认 true
     */
    @Value("${aml.slow-query.enabled:true}")
    private boolean enabled;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!enabled) {
            return invocation.proceed();
        }

        long startTime = System.currentTimeMillis();
        Object result;

        try {
            result = invocation.proceed();
        } finally {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            if (duration >= slowQueryThreshold) {
                MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
                Object parameter = invocation.getArgs().length > 1 ? invocation.getArgs()[1] : null;
                BoundSql boundSql = mappedStatement.getBoundSql(parameter);
                String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();

                log.warn("[慢查询] 执行时间: {}ms, 阈值: {}ms, Mapper: {}, SQL: {}",
                        duration, slowQueryThreshold, mappedStatement.getId(), sql);
            }
        }

        return result;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 可通过 properties 配置
    }
}
