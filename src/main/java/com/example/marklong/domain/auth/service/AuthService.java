package com.example.marklong.domain.auth.service;

import com.example.marklong.domain.auth.dto.LoginRequest;
import com.example.marklong.domain.auth.dto.RotateResult;
import com.example.marklong.domain.auth.dto.SignupRequest;
import com.example.marklong.domain.auth.dto.TokenResponse;
import com.example.marklong.domain.auth.repository.RefreshTokenRedisRepository;
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

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtProvider;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final StringRedisTemplate stringRedisTemplate;

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
        String refreshToken = refreshTokenRedisRepository.save(user.getId());

        return TokenResponse.of(accessToken, refreshToken);
    }

    public TokenResponse reissue(String refreshToken) {
        RotateResult result = refreshTokenRedisRepository.rotate(refreshToken);

        Long userId = result.userId();
        String newRefreshToken = result.newToken();

        User user = userRepository.findUserByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String newAccessToken = jwtProvider.createAccessToken(userId, user.getRole());

        return TokenResponse.of(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId, String accessToken) {
        refreshTokenRedisRepository.revokeAll(userId);


        // AT blacklist 등록 => phase4
//        long remainingTime = jwtProvider.getExpiration(accessToken);
//
//        stringRedisTemplate.opsForValue().set(
//                "blacklist:" + accessToken,
//                "logged_out",
//                remainingTime, TimeUnit.MILLISECONDS
//        );
    }

    private User authenticate(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getProvider() != OAuthProvider.LOCAL) {
            throw new BusinessException(ErrorCode.OAUTH_USER_LOGIN_DENIED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        return user;
    }
}
