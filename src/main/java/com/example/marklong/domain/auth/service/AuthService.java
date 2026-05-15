package com.example.marklong.domain.auth.service;

import com.example.marklong.domain.auth.domain.RefreshToken;
import com.example.marklong.domain.auth.dto.LoginRequest;
import com.example.marklong.domain.auth.dto.SignupRequest;
import com.example.marklong.domain.auth.dto.TokenResponse;
import com.example.marklong.domain.auth.repository.RefreshTokenRepository;
import com.example.marklong.domain.user.domain.OAuthProvider;
import com.example.marklong.domain.user.domain.Role;
import com.example.marklong.domain.user.domain.User;
import com.example.marklong.domain.user.repository.UserRepository;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import com.example.marklong.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final RefreshTokenService refreshTokenService;

    public void signup(SignupRequest request) {
        // 비활성 계정 닉네임은 중복 허용
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .role(Role.USER)
                .provider(OAuthProvider.LOCAL)
                .build();
        userRepository.save(user);
    }

    public TokenResponse login(LoginRequest request) {
        User user = authenticate(request);

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtProvider.createRefreshToken();

        // refresh 저장
        refreshTokenService.save(user.getId(), refreshToken);

        return TokenResponse.of(accessToken, refreshToken);
    }

    public TokenResponse reissue(String refreshToken) {
        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (token.isExpired()) {
            // 만료되었으면 db에서도 삭제
            refreshTokenRepository.delete(token);
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }

        // soft delete된 유저는 재발급 불가
        User user = userRepository.findUserByIdAndDeletedAtIsNull(token.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String newAccessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String newRefreshToken = jwtProvider.createRefreshToken();

        // RTR: 기존 토큰을 새 토큰으로 교체
        token.rotate(newRefreshToken, jwtProvider.getRefreshExpiry());
        // 이전 refreshToken을 Redis blacklist에 추가 (짧은 TTL) ? 그냥 redis, db에서 지우고 끝 아닌가?

        return TokenResponse.of(newAccessToken, newRefreshToken);
    }

    /**
     * @param accessToken 로그아웃 시 무효화할 access token
     *                    (추후 Redis blacklist에 남은 만료 시간만큼 등록 예정)
     */
    public void logout(Long userId, String accessToken) {
        refreshTokenRepository.deleteByUserId(userId);
        // TODO: Redis 도입 시 → accessToken을 blacklist:{accessToken} 키로 Redis에 저장
        //       TTL = jwtProvider.getRemainingExpiry(accessToken)
    }

    private User authenticate(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        return user;
    }
}
