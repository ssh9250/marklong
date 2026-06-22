package com.example.marklong.security.auth;

import com.example.marklong.domain.user.domain.Role;

public record AuthUser(
        Long userId,
        Role role
) {
}
