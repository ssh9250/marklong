package com.example.marklong.domain.auth.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class TokenResponse {
    private final String acessToken;
    private final String refreshToken;

    public static TokenResponse of(String token, String refreshToken) {
        return new TokenResponse(token, refreshToken);
    }
}
