package com.example.marklong.domain.auth.service;

import com.example.marklong.domain.auth.domain.RefreshToken;
import com.example.marklong.domain.auth.repository.RefreshTokenJpaRepository;
import com.example.marklong.domain.auth.repository.RefreshTokenRepository;
import com.example.marklong.domain.user.domain.User;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import com.example.marklong.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshTokenValidityMs;

    public void saveOrUpdate(Long userId, String token) {
        // 저장 시 db, redis 기존 토큰들 전부 삭제
        refreshTokenRepository.save(userId, token, refreshTokenValidityMs);
    }

    public void validate(Long userid, String token) {
        if (!refreshTokenRepository.isValid(userid, token)) {
            delete(userid); // 일단 토큰 무효화부터
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    public void delete(Long userId) {
        refreshTokenRepository.delete(userId);
    }

    public Long extractUserId(String token) {
        return refreshTokenJpaRepository.findByToken(token).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN)).getUserId();
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void deleteExpiredTokens() {
        refreshTokenJpaRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
    }
}
