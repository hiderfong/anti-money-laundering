package com.insurance.aml.common.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 慢查询拦截器单元测试
 */
class SlowQueryInterceptorTest {

    private SlowQueryInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new SlowQueryInterceptor();
        Properties props = new Properties();
        interceptor.setProperties(props);
    }

    @Test
    @DisplayName("plugin方法应返回代理对象")
    void plugin_shouldReturnProxy() {
        Object target = mock(Executor.class);
        Object result = interceptor.plugin(target);
        assertNotNull(result);
    }

    @Test
    @DisplayName("setProperties不应抛出异常")
    void setProperties_shouldNotThrow() {
        Properties props = new Properties();
        assertDoesNotThrow(() -> interceptor.setProperties(props));
    }
}
