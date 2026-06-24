package com.example.marklong.security.jwt;

import com.example.marklong.domain.auth.repository.RefreshTokenRedisRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = jwtTokenProvider.resolveToken(request);
        String blacklistKey = "blacklist:" + token;

        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // phase4
//        if (stringRedisTemplate.hasKey(blacklistKey)) {
//             reject(response);
//             return;
//         }

        if (!jwtTokenProvider.validateToken(token)) {
            reject(response);
            return;
        }

        Long userId = jwtTokenProvider.getUserId(token);
        long issuedAt = jwtTokenProvider.getIssuedAt(token) / 1000;

        Optional<Long> revokedAfter = refreshTokenRedisRepository.getRevokedAfter(userId);
        if (revokedAfter.isPresent() && revokedAfter.get() > issuedAt) {
            reject(response);
            return;
        }

        Authentication authentication = jwtTokenProvider.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"Unauthorized\"}");
    }
}
