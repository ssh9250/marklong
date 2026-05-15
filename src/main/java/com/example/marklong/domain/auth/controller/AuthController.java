package com.example.marklong.domain.auth.controller;

import com.example.marklong.domain.auth.dto.LoginRequest;
import com.example.marklong.domain.auth.dto.ReissueRequest;
import com.example.marklong.domain.auth.dto.SignupRequest;
import com.example.marklong.domain.auth.dto.TokenResponse;
import com.example.marklong.domain.auth.service.AuthService;
import com.example.marklong.global.response.ApiResponse;
import com.example.marklong.security.auth.CustomUserDetails;
import com.example.marklong.security.jwt.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup")
    @Operation(summary = "회원가입")
    public ResponseEntity<ApiResponse<Void>> signup(
            @RequestBody @Valid SignupRequest request
    ) {
        authService.signup(request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일·비밀번호 인증 후 Access/Refresh Token을 반환합니다.")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @RequestBody @Valid LoginRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 재발급", description = "Refresh Token으로 새 Access/Refresh Token 쌍을 발급합니다. (RTR 방식)")
    public ResponseEntity<ApiResponse<TokenResponse>> reissue(
            @RequestBody @Valid ReissueRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(authService.reissue(request.getRefreshToken())));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "Refresh Token을 무효화합니다. Redis 도입 시 Access Token blacklist 등록 예정.")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        String accessToken = jwtTokenProvider.resolveToken(httpRequest);
        authService.logout(userDetails.getUserId(), accessToken);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── OAuth2 (미구현) ─────────────────────────────────────

    @GetMapping("/oauth2/{provider}")
    @Operation(summary = "OAuth2 로그인 요청 (미구현)", description = "provider: kakao | google | naver")
    public ResponseEntity<ApiResponse<Void>> oauthLogin(
            @PathVariable String provider
    ) {
        // TODO: OAuth2 로그인 구현
        throw new UnsupportedOperationException("OAuth2 로그인은 아직 구현되지 않았습니다.");
    }

    @GetMapping("/oauth2/callback/{provider}")
    @Operation(summary = "OAuth2 콜백 처리 (미구현)")
    public ResponseEntity<ApiResponse<TokenResponse>> oauthCallback(
            @PathVariable String provider,
            @RequestParam String code
    ) {
        // TODO: OAuth2 콜백 처리 및 토큰 발급 구현
        throw new UnsupportedOperationException("OAuth2 콜백은 아직 구현되지 않았습니다.");
    }
}
