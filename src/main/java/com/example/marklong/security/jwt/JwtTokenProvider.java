package com.example.marklong.security.jwt;

import com.example.marklong.domain.user.domain.Role;
import com.example.marklong.security.auth.CustomUserDetailsService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret key 의 길이는 최소 32바이트 이상이여야 합니다. 현재 길이 : " + secretBytes.length);
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String createAccessToken(Long userId, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpirationMs))
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken() {
        return UUID.randomUUID().toString();
    }

    public long getExpiration(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Date expiration = claims.getExpiration();
            Date now = new Date();

            long diff =  expiration.getTime() - now.getTime();
            return diff > 0 ? diff : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.info("token expired");
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Long.parseLong(subject);
        } catch (JwtException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // todo: 트래픽 증가 시 로직 바꾸기
        // 현재는 필터에서 매 요청 시 DB 조회를 통해 유저 정보 전체를 security context에 올리고 있지만,
        // 트래픽이 증가하면 getAuthentication()을 토큰 클레임 기반으로 변경하거나,
        // loadUserById() 결과를 Redis로 캐싱하는 방향으로 개선 필요
//        Long userId = Long.parseLong(claims.getSubject());
//        String role = claims.get("role", String.class);
//        SimpleGrantedAuthority simpleGrantedAuthority = new SimpleGrantedAuthority("ROLE_" + role);
//        return new UsernamePasswordAuthenticationToken(userId, null, List.of(simpleGrantedAuthority));

        Long userId = getUserId(token);
        UserDetails userDetails = userDetailsService.loadUserById(userId);
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }
}
