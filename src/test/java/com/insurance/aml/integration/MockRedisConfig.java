package com.insurance.aml.integration;

import com.insurance.aml.module.system.repository.AuditLogElasticsearchRepository;
import org.mockito.Mockito;
import org.neo4j.driver.Driver;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试环境Mock配置
 * 当外部服务不可用时提供Mock的Bean
 */
@TestConfiguration
public class MockRedisConfig {

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        RedisConnectionFactory factory = Mockito.mock(RedisConnectionFactory.class);
        RedisConnection connection = Mockito.mock(RedisConnection.class);
        Mockito.when(factory.getConnection()).thenReturn(connection);
        Mockito.when(connection.ping()).thenReturn("PONG");
        return factory;
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class);
        RedisConnectionFactory connectionFactory = redisConnectionFactory();
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        HashOperations<String, Object, Object> hashOperations = Mockito.mock(HashOperations.class);
        Map<String, String> valueStore = new ConcurrentHashMap<>();
        Map<String, Map<Object, Object>> hashStore = new ConcurrentHashMap<>();
        Mockito.when(template.opsForValue()).thenReturn(valueOperations);
        Mockito.when(template.opsForHash()).thenReturn(hashOperations);
        Mockito.when(template.getConnectionFactory()).thenReturn(connectionFactory);
        Mockito.doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            valueStore.remove(key);
            hashStore.remove(key);
            return Boolean.TRUE;
        }).when(template).delete(Mockito.anyString());
        Mockito.when(template.expire(Mockito.anyString(), Mockito.anyLong(), Mockito.any())).thenReturn(Boolean.TRUE);
        Mockito.doAnswer(invocation -> {
            valueStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.any());
        Mockito.doAnswer(invocation -> {
            valueStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(Mockito.anyString(), Mockito.anyString());
        Mockito.when(valueOperations.get(Mockito.anyString()))
                .thenAnswer(invocation -> valueStore.get(invocation.getArgument(0)));
        Mockito.when(valueOperations.increment(Mockito.anyString(), Mockito.anyDouble()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    double delta = invocation.getArgument(1);
                    double current = Double.parseDouble(valueStore.getOrDefault(key, "0"));
                    double next = current + delta;
                    valueStore.put(key, String.valueOf(next));
                    return next;
                });
        Mockito.doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object hashKey = invocation.getArgument(1);
            Object value = invocation.getArgument(2);
            hashStore.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(hashKey, value);
            return null;
        }).when(hashOperations).put(Mockito.anyString(), Mockito.any(), Mockito.any());
        Mockito.when(hashOperations.get(Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> {
                    Map<Object, Object> values = hashStore.get(invocation.getArgument(0));
                    return values == null ? null : values.get(invocation.getArgument(1));
                });
        return template;
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = Mockito.mock(RedisTemplate.class);
        Mockito.when(template.execute(Mockito.any(), Mockito.anyList(), Mockito.any()))
                .thenReturn(java.util.List.of(0L, 0L, 0L));
        Mockito.when(template.execute(Mockito.any(), Mockito.anyList(), Mockito.any(), Mockito.any()))
                .thenReturn(java.util.List.of(0L, 0L, 0L));
        Mockito.when(template.execute(Mockito.any(), Mockito.anyList(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(java.util.List.of(0L, 0L, 0L));
        Mockito.when(template.execute(Mockito.any(), Mockito.anyList(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(java.util.List.of(0L, 0L, 0L));
        Mockito.when(template.execute(Mockito.any(), Mockito.anyList(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(java.util.List.of(0L, 0L, 0L));
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
