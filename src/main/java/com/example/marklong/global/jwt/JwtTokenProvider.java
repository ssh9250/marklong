package com.example.marklong.global.jwt;

import com.study.shop.security.auth.CustomUserDetailsService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {
    private final CustomUserDetailsService userDetailsService;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration-ms}")
    private long accessExpirationMs;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private Key secretKey;

    @PostConstruct
    public void init() {
        // Secret 길이 검증, UTF-8 인코딩 명시
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret key 의 길이는 최소 32바이트 이상이여야 합니다. 현재 길이 : " + secretBytes.length);
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String createAccessToken(String email) {
        return createToken(email, accessExpirationMs);
    }

    public String createRefreshToken(String email) {
        return createToken(email, refreshExpirationMs);
    }

    private String createToken(String email, long expiration) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // authorization 헤더에서 bearer 토큰 추출
    public String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    // 유효성 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.info("token expired");
            return false; // 만료 -> access token 만료 로직에서 refresh token 유효성 검증 완료 (refresh service)
        } catch (JwtException | IllegalArgumentException e) {
            return false;   //  기타 버그
        }
    }

    public long getExpiration(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build().parseClaimsJws(token).getBody();

            Date expiration = claims.getExpiration();
            Date now = new Date();

            return expiration.getTime() - now.getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    //토큰에서 email 추출
    public String getEmail(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token).getBody();
        return claims.getSubject(); // user email
    }

    // 토큰 기반 인증 객체 생성
    public Authentication getAuthentication(String token) {
        String email = getEmail(token);
        var userDetails = userDetailsService.loadUserByUsername(email);

        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }
}
