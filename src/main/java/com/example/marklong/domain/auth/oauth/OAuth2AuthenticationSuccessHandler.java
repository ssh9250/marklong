package com.example.marklong.domain.auth.oauth;

import com.example.marklong.domain.auth.repository.RefreshTokenRedisRepository;
import com.example.marklong.domain.auth.service.RefreshTokenService;
import com.example.marklong.domain.user.domain.User;
import com.example.marklong.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;


@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtTokenProvider jwtProvider;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;

    @Value("${oauth2.redirect-uri}")
    private String redirectUri;

    @Value("${jwt.access-expiration-ms}")
    private long accessExpirationMs;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        CustomOauth2User oAuth2User = (CustomOauth2User) authentication.getPrincipal();
        User user = oAuth2User.getUser();

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtProvider.createRefreshToken();

        // 1. Access Token을 쿠키에 저장
        ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", accessToken)
                .path("/")
                .sameSite("Lax")
                .httpOnly(false)
                .secure(true)
                .maxAge(accessExpirationMs)    // 30분
                .build();

        // 2. Refresh Token을 HttpOnly 쿠키에 저장
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", refreshToken)
                .path("/")
                .sameSite("Lax")
                .httpOnly(true)  // 브라우저 JavaScript에서 접근 불가능하게 설정 (XSS 방어)
                .secure(true)    // HTTPS 환경에서만 전송
                .maxAge(refreshExpirationMs)  // 14일
                .build();

        // Response 헤더에 쿠키 추가
        response.addHeader("Set-Cookie", accessTokenCookie.toString());
        response.addHeader("Set-Cookie", refreshTokenCookie.toString());

        // 임시코드 방법도 고려

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
//                .queryParam("accessToken", accessToken)
//                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
