package com.example.marklong.domain.auth.service;

import com.example.marklong.domain.auth.domain.RefreshToken;
import com.example.marklong.domain.auth.repository.RefreshTokenRepository;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import com.example.marklong.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshTokenValidityMs;

    public void save(Long userId, String token) {
        // 저장 시 db, redis 기존 토큰들 전부 삭제
        refreshTokenRepository.save(userId, token,  refreshTokenValidityMs);
    }

    public void validate(Long userid, String token) {
        if (!refreshTokenRepository.isValid(userid, token)) throw new BusinessException(ErrorCode.INVALID_TOKEN);


    }
}
