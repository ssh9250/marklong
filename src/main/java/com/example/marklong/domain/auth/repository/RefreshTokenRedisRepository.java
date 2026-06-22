package com.example.marklong.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepository {
    private final StringRedisTemplate stringRedisTemplate;

    /*
    * redis 설계
    * rt:{hashedToken}, value = {userId, status, issuedAt, expiresAt}
    * user:{userId}:rtRevokedAfter , value = ttl or issuedAt or version
    *
    * */

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    public void save(Long userId) {

    }

    public boolean rotate(String token, Long userId) {
        // token -> hashed and compare -> hit/miss
        // hit -> ACTIVE/ROTATE
        // ACTIVE -> redis search and change status, add new token
        // ROTATE -> reuse detection logic
        // miss -> throw new invalid token exception
    }

    public void revokeAll(Long userId) {

    }

    public Optional<Long> getRevokedAfter(Long userId) {

    }

    private Map<Object, Object> findByToken(String token) {

    }
}
