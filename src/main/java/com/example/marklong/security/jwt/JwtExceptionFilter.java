package com.example.marklong.security.jwt;

import com.example.marklong.global.exception.ErrorCode;
import com.example.marklong.security.util.SecurityResponseUtil;
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
            log.error("JWT Token expired: {}", e.getMessage());
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.EXPIRED_TOKEN);
        } catch (SignatureException e) {
            log.error("JWT Signature validation failed: {}", e.getMessage());
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.INVALID_TOKEN);
        } catch (MalformedJwtException e) {
            log.error("JWT Malformed: {}", e.getMessage());
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.INVALID_TOKEN);
        } catch (UnsupportedJwtException e) {
            log.error("JWT Unsupported: {}", e.getMessage());
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.INVALID_TOKEN);
        } catch (IllegalArgumentException e) {
            log.error("JWT Token is empty: {}", e.getMessage());
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.INVALID_TOKEN);
        } catch (JwtException e) {
            log.error("JWT Exception: {}", e.getMessage());
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.INVALID_TOKEN);
        } catch (Exception e) {
            log.error("Unexpected exception in JWT filter: {}", e.getMessage(), e);
            SecurityResponseUtil.sendErrorResponse(response, ErrorCode.UNAUTHORIZED);
        }
    }
}
