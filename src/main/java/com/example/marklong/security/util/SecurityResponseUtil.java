package com.example.marklong.security.util;

import com.example.marklong.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SecurityResponseUtil {

    private SecurityResponseUtil() {}

    public static void sendErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
                "{\"success\":false,\"code\":\"%s\",\"message\":\"%s\"}",
                errorCode.getCode(), errorCode.getMessage()
        ));
    }
}
