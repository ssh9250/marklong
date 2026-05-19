package com.example.marklong.domain.user.domain;

import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;

public enum OAuthProvider {
    LOCAL, GOOGLE, KAKAO, NAVER;

    public static OAuthProvider from(String registrationId) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> GOOGLE;
            case "kakao" -> KAKAO;
            case "naver" -> NAVER;
            default -> throw new BusinessException(ErrorCode.UNAUTHORIZED);
        };
    }
}
