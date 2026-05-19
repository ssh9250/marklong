package com.example.marklong.domain.auth.oauth;

import com.example.marklong.domain.user.domain.OAuthProvider;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;

import java.util.Map;

public class Oauth2UserInfoFactory {
    public static Oauth2UserInfo getOauth2UserInfo(
            OAuthProvider provider,
            Map<String, Object> attributes
    ) {
        return switch (provider) {
            case GOOGLE -> new GoogleUserInfo(attributes);
            case KAKAO -> new KakaoUserInfo(attributes);
            case NAVER -> new NaverUserInfo(attributes);
            case LOCAL -> throw new BusinessException(ErrorCode.UNAUTHORIZED);
        };
    }
}
