package com.insurance.aml.integration;

import com.insurance.aml.module.system.repository.AuditLogElasticsearchRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 测试环境Redis Mock配置
 * 当Redis不可用时提供Mock的StringRedisTemplate
 */
@TestConfiguration
public class MockRedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        Mockito.when(template.opsForValue()).thenReturn(valueOperations);
        Mockito.when(template.delete(Mockito.anyString())).thenReturn(Boolean.TRUE);
        return template;
    }

    @Bean
    public AuditLogElasticsearchRepository auditLogElasticsearchRepository() {
        return Mockito.mock(AuditLogElasticsearchRepository.class);
    }

    @Bean
    public ElasticsearchOperations elasticsearchOperations() {
        return Mockito.mock(ElasticsearchOperations.class);
    }
}
