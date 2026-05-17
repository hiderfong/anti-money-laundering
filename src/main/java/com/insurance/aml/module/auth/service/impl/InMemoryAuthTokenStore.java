package com.insurance.aml.module.auth.service.impl;

import com.insurance.aml.module.auth.service.AuthTokenStore;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存令牌状态存储。
 *
 * Redis 令牌仓库不可用或被禁用时作为兜底实现，避免认证链路因外部依赖异常无法启动。
 * RedisAuthTokenStore 可用时会以 @Primary 注入。
 */
@Component
public class InMemoryAuthTokenStore implements AuthTokenStore {

    private final Map<Long, ExpiringValue> refreshTokens = new ConcurrentHashMap<>();
    private final Map<String, ExpiringValue> accessTokenBlacklist = new ConcurrentHashMap<>();
    private final Map<Long, ExpiringValue> userTokenBlacklist = new ConcurrentHashMap<>();

    @Override
    public void saveRefreshToken(Long userId, String refreshToken, Duration ttl) {
        refreshTokens.put(userId, new ExpiringValue(refreshToken, expiresAt(ttl)));
    }

    @Override
    public String getRefreshToken(Long userId) {
        ExpiringValue value = refreshTokens.get(userId);
        if (value == null || value.isExpired()) {
            refreshTokens.remove(userId);
            return null;
        }
        return value.value();
    }

    @Override
    public void deleteRefreshToken(Long userId) {
        refreshTokens.remove(userId);
    }

    @Override
    public void blacklistAccessToken(String accessToken, Duration ttl) {
        if (accessToken != null && !accessToken.isBlank()) {
            accessTokenBlacklist.put(accessToken, new ExpiringValue("logout", expiresAt(ttl)));
        }
    }

    @Override
    public void blacklistUserTokens(Long userId, Duration ttl) {
        userTokenBlacklist.put(userId, new ExpiringValue("logout", expiresAt(ttl)));
    }

    @Override
    public boolean isAccessTokenBlacklisted(String accessToken) {
        ExpiringValue value = accessTokenBlacklist.get(accessToken);
        if (value == null || value.isExpired()) {
            accessTokenBlacklist.remove(accessToken);
            return false;
        }
        return true;
    }

    private long expiresAt(Duration ttl) {
        return System.currentTimeMillis() + Math.max(ttl.toMillis(), 1L);
    }

    private record ExpiringValue(String value, long expiresAtMillis) {
        private boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
    }
}
