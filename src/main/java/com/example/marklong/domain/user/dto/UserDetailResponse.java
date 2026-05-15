package com.example.marklong.domain.user.dto;

import com.example.marklong.domain.user.domain.OAuthProvider;
import com.example.marklong.domain.user.domain.Role;
import com.example.marklong.domain.user.domain.User;

public record UserDetailResponse(
        Long userId,
        String email,
        String nickname,
        Role role,
        OAuthProvider provider
) {
    public static UserDetailResponse from(User user) {
        return new UserDetailResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getProvider()
        );
    }
}
