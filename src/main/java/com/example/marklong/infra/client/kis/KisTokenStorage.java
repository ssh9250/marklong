package com.example.marklong.infra.client.kis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@RequiredArgsConstructor
@Component
public class KisTokenStorage {
    private final StringRedisTemplate stringRedisTemplate;
    private static final String ACCESS_TOKEN_KEY = "kis:access-token";
    private final KisAuthClient kisAuthClient;

    public void saveAccessToken(String accessToken) {
        stringRedisTemplate.opsForValue().set(ACCESS_TOKEN_KEY, accessToken, Duration.ofDays(1));
    }

    public String getOrIssueToken() {
        String token = getAccessToken();
        if (token != null) {
            return token;
        }
        token = kisAuthClient.requestNewAccessToken();
        saveAccessToken(token);
        return token;
    }

    private String getAccessToken() {
        return stringRedisTemplate.opsForValue().get(ACCESS_TOKEN_KEY);
    }
}
