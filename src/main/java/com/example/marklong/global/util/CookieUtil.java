package com.example.marklong.global.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

public class CookieUtil {

    public static ResponseCookie createRefreshTokenCookie(String refreshToken, long ttl) {
        return ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .sameSite("Lax")
                .maxAge(Duration.ofMillis(ttl))
                .path("/")
                .build();
    }
}
