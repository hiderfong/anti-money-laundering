package com.insurance.aml.module.auth.service;

import java.time.Duration;

/**
 * 认证令牌状态存储。
 */
public interface AuthTokenStore {

    void saveRefreshToken(Long userId, String refreshToken, Duration ttl);

    String getRefreshToken(Long userId);

    void deleteRefreshToken(Long userId);

    void blacklistAccessToken(String accessToken, Duration ttl);

    void blacklistUserTokens(Long userId, Duration ttl);

    boolean isAccessTokenBlacklisted(String accessToken);
}
