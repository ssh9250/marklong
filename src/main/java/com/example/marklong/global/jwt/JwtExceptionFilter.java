package com.example.marklong.global.jwt;

import com.study.shop.global.exception.ErrorCode;
import com.study.shop.security.exception.ExpiredTokenException;
import com.study.shop.security.exception.InvalidTokenException;
import com.study.shop.security.util.SecurityResponseUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 예외를 처리하는 필터
 * JwtAuthenticationFilter에서 발생하는 예외를 잡아 적절한 응답 반환
 */
@Slf4j
@Component
public class JwtExceptionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            // 토큰 만료
            log.error("JWT Token expired: {}", e.getMessage());
            request.setAttribute("errorCode", ErrorCode.EXPIRED_TOKEN);
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.EXPIRED_TOKEN);

        } catch (SignatureException e) {
            // 서명 검증 실패
            log.error("JWT Signature validation failed: {}", e.getMessage());
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.INVALID_TOKEN);

        } catch (MalformedJwtException e) {
            // 잘못된 토큰 형식
            log.error("JWT Malformed: {}", e.getMessage());
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.INVALID_TOKEN);

        } catch (UnsupportedJwtException e) {
            // 지원하지 않는 토큰
            log.error("JWT Unsupported: {}", e.getMessage());
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.INVALID_TOKEN);

        } catch (IllegalArgumentException e) {
            // JWT 토큰이 비어있음
            log.error("JWT Token is empty: {}", e.getMessage());
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.INVALID_TOKEN);

        } catch (InvalidTokenException e) {
            // 커스텀 예외 - 유효하지 않은 토큰
            log.error("Invalid token: {}", e.getMessage());
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.INVALID_TOKEN);

        } catch (ExpiredTokenException e) {
            // 커스텀 예외 - 만료된 토큰
            log.error("Expired token: {}", e.getMessage());
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.EXPIRED_TOKEN);

        } catch (JwtException e) {
            // 기타 JWT 관련 예외
            log.error("JWT Exception: {}", e.getMessage());
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.INVALID_TOKEN);

        } catch (Exception e) {
            // 예상치 못한 예외
            log.error("Unexpected exception in JWT filter: {}", e.getMessage(), e);
            SecurityResponseUtil.sendErrorResponse(
                    response,
                    ErrorCode.UNAUTHORIZED,
                    "인증 처리 중 오류가 발생했습니다"
            );
        }
    }
}
