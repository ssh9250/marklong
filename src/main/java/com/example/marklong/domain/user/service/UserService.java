package com.example.marklong.domain.user.service;

import com.example.marklong.domain.auth.dto.SignupRequest;
import com.example.marklong.domain.user.domain.OAuthProvider;
import com.example.marklong.domain.user.domain.Role;
import com.example.marklong.domain.user.domain.User;
import com.example.marklong.domain.user.dto.UserCreateCommand;
import com.example.marklong.domain.user.dto.UserDetailResponse;
import com.example.marklong.domain.user.repository.UserRepository;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;

    private void validateEmail(String email) {
        // 비활성 계정 닉네임은 중복 허용
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
    }

    private void validateNickname(String nickname) {
        if (userRepository.existsByNicknameAndDeletedAtIsNull(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
    }

    private void validateOwner(Long requestUserId, Long userId) {
        if (!requestUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    public User createUser(UserCreateCommand request) {
        User user = User.builder()
                .email(request.email())
                .password(request.password())
                .nickname(request.nickname())
                .role(Role.USER)
                .provider(OAuthProvider.LOCAL)
                .build();
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserDetailResponse getUserInfo(Long userId) {
        User user = getUserOrThrow(userId);
        return UserDetailResponse.from(user);
    }
}
