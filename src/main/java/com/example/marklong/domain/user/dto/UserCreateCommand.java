package com.example.marklong.domain.user.dto;

public record UserCreateCommand(
        String email,
        String password,
        String nickname
) {
}
