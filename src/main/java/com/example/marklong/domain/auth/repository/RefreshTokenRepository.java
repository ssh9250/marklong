package com.example.marklong.domain.auth.repository;

import com.example.marklong.domain.auth.domain.RefreshToken;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {
    private final StringRedisTemplate stringRedisTemplate;
    private final RefreshTokenJpaRepository jpaRepository;
    private static final String KEY_PREFIX = "refresh:";

    public void save(Long userId, String token, long ttlMs) {
        String key = KEY_PREFIX + userId;
        LocalDateTime expiration = LocalDateTime.now().plusSeconds(ttlMs / 1000);

        RefreshToken refreshToken = jpaRepository.findByUserId(userId)
                .orElse(RefreshToken.builder()
                        .userId(userId)
                        .token(token)
                        .expiresAt(expiration)
                        .build());
        // 있든 없든 세팅
        refreshToken.rotate(token, expiration);
        jpaRepository.save(refreshToken);

        stringRedisTemplate.opsForValue().set(key, token, Duration.ofMillis(ttlMs));
    }

    public String findByUserId(Long userId) {
        String key = KEY_PREFIX + userId;
        String token = stringRedisTemplate.opsForValue().get(key);
        // 캐시 히트
        if (token != null) {
            return token;
        }

        // 미스
        RefreshToken refreshToken = jpaRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        Duration remaining = Duration.between(LocalDateTime.now(), refreshToken.getExpiresAt());
        if (!remaining.isNegative()) {
            stringRedisTemplate.opsForValue().set(key, refreshToken.getToken(), remaining);
        }
        return refreshToken.getToken();
    }

    public void delete(Long userId) {
        String key = KEY_PREFIX + userId;
        stringRedisTemplate.delete(KEY_PREFIX + userId);
        jpaRepository.deleteByUserId(userId);
    }

    public boolean isValid(Long userId, String token) {
        String stored = findByUserId(userId);  // Redis 먼저 → miss면 DB
        return token.equals(stored);
    }
}
