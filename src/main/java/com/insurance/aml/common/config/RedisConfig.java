package com.insurance.aml.common.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.HashMap;
import java.util.Map;

import java.time.Duration;

/**
 * Redis 配置类
 *
 * 功能说明：
 * 1. 配置 RedisTemplate 序列化方式 - Key使用String序列化，Value使用JSON序列化
 * 2. 配置 RedisCacheManager - 统一缓存管理，设置默认TTL和Key前缀
 * 3. 支持Java8日期时间类型序列化
 *
 * @author AML Team
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    /** 默认缓存过期时间：24小时 */
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    /** 缓存Key前缀 */
    private static final String CACHE_PREFIX = "aml:";

    /**
     * 配置 RedisTemplate
     * 使用Jackson2JsonRedisSerializer序列化value，解决乱码问题
     *
     * @param connectionFactory Redis连接工厂
     * @return RedisTemplate<String, Object>
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("初始化 RedisTemplate 配置...");
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key序列化使用StringRedisSerializer
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value序列化使用Jackson2JsonRedisSerializer
        GenericJackson2JsonRedisSerializer jsonSerializer = createJsonSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        log.info("RedisTemplate 配置完成：Key=String序列化, Value=JSON序列化");
        return template;
    }

    /**
     * 配置缓存管理器
     * 统一管理所有@Cacheable注解的缓存行为
     *
     * @param connectionFactory Redis连接工厂
     * @return CacheManager
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("初始化 Redis CacheManager 配置，默认TTL={}小时...", DEFAULT_TTL.toHours());

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // 设置默认TTL
                .entryTtl(DEFAULT_TTL)
                // 设置Key前缀
                .computePrefixWith(cacheName -> CACHE_PREFIX + cacheName + ":")
                // 设置Key序列化
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                // 设置Value序列化
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(createJsonSerializer()))
                // 不缓存null值
                .disableCachingNullValues();

        // 为不同缓存空间配置不同的TTL
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        // 客户详情缓存: 10分钟
        cacheConfigurations.put("customer", config.entryTtl(Duration.ofMinutes(10)));
        // 字典数据缓存: 30分钟
        cacheConfigurations.put("dict", config.entryTtl(Duration.ofMinutes(30)));
        // 白名单缓存: 5分钟
        cacheConfigurations.put("whitelist", config.entryTtl(Duration.ofMinutes(5)));

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();


        log.info("Redis CacheManager 配置完成，Key前缀={}", CACHE_PREFIX);
        return cacheManager;
    }

    /**
     * 创建JSON序列化器
     * 配置ObjectMapper支持Java8日期类型和类型信息嵌入
     *
     * @return GenericJackson2JsonRedisSerializer
     */
    private GenericJackson2JsonRedisSerializer createJsonSerializer() {
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 启用类型信息，用于反序列化时还原正确的类型
        om.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        // 注册Java8时间模块
        om.registerModule(new JavaTimeModule());
        return new GenericJackson2JsonRedisSerializer(om);
    }
}
