package com.insurance.aml.integration;

import com.insurance.aml.module.system.repository.AuditLogElasticsearchRepository;
import org.mockito.Mockito;
import org.neo4j.driver.Driver;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * 测试环境Mock配置
 * 当外部服务不可用时提供Mock的Bean
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

    // Neo4j Mock Beans
    @Bean
    public Driver neo4jDriver() {
        return Mockito.mock(Driver.class);
    }

    @Bean
    public Neo4jClient neo4jClient() {
        return Mockito.mock(Neo4jClient.class);
    }

    @Bean
    public Neo4jTemplate neo4jTemplate() {
        return Mockito.mock(Neo4jTemplate.class);
    }

    @Bean
    public Neo4jMappingContext neo4jMappingContext() {
        return Mockito.mock(Neo4jMappingContext.class);
    }

    @Bean
    public PlatformTransactionManager neo4jTransactionManager() {
        PlatformTransactionManager manager = Mockito.mock(PlatformTransactionManager.class);
        Mockito.when(manager.getTransaction(Mockito.any())).thenReturn(new DefaultTransactionStatus(null, true, true, false, false, null));
        return manager;
    }
}
