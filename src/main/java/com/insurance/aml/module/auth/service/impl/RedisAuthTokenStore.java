package com.insurance.aml.module.auth.service.impl;

import com.insurance.aml.module.auth.service.AuthTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis 令牌状态存储。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(name = "aml.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisAuthTokenStore implements AuthTokenStore {

    private static final String REFRESH_TOKEN_PREFIX = "jwt:refresh:";
    private static final String JWT_BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String USER_BLACKLIST_PREFIX = "jwt:blacklist:user:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void saveRefreshToken(Long userId, String refreshToken, Duration ttl) {
        redisTemplate.opsForValue().set(refreshKey(userId), refreshToken, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public String getRefreshToken(Long userId) {
        return redisTemplate.opsForValue().get(refreshKey(userId));
    }

    @Override
    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(refreshKey(userId));
    }

    @Override
    public void blacklistAccessToken(String accessToken, Duration ttl) {
        redisTemplate.opsForValue().set(tokenBlacklistKey(accessToken), "logout", ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void blacklistUserTokens(Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(userBlacklistKey(userId), "logout", ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isAccessTokenBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(tokenBlacklistKey(accessToken)));
    }

    private String refreshKey(Long userId) {
        return REFRESH_TOKEN_PREFIX + userId;
    }

    private String tokenBlacklistKey(String accessToken) {
        return JWT_BLACKLIST_PREFIX + accessToken;
    }

    private String userBlacklistKey(Long userId) {
        return USER_BLACKLIST_PREFIX + userId;
    }
}
