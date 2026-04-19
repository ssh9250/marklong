package com.example.marklong.domain.auth.service;

import com.example.marklong.domain.auth.domain.OAuthProvider;
import com.example.marklong.domain.auth.domain.RefreshToken;
import com.example.marklong.domain.auth.domain.Role;
import com.example.marklong.domain.auth.domain.User;
import com.example.marklong.domain.auth.dto.LoginRequest;
import com.example.marklong.domain.auth.dto.SignupRequest;
import com.example.marklong.domain.auth.dto.TokenResponse;
import com.example.marklong.domain.auth.repository.RefreshTokenRepository;
import com.example.marklong.domain.auth.repository.UserRepository;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import com.example.marklong.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtProvider;

    public void signup(SignupRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        User user = User.builder()
                .email(request.getEmail())
                .password(request.getPassword())
                .nickname(request.getNickname())
                .role(Role.ROLE_USER)
                .provider(OAuthProvider.LOCAL)
                .build();
        userRepository.save(user);
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtProvider.createRefreshToken();

        refreshTokenRepository.findByUserId(user.getId())
                .ifPresentOrElse(
                        token -> token.rotate(refreshToken, jwtProvider.getRefreshExpiry()),
                        () -> refreshTokenRepository.save(RefreshToken.builder()
                                .userId(user.getId())
                                .token(refreshToken)
                                .expiresAt(jwtProvider.getRefreshExpiry())
                                .build())
                );

        return TokenResponse.of(accessToken, refreshToken);
    }

    public TokenResponse reissue(String refreshToken) {
        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (token.isExpired()) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String newAccessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String newRefreshToken = jwtProvider.createRefreshToken();
        token.rotate(newRefreshToken, jwtProvider.getRefreshExpiry());

        return TokenResponse.of(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
