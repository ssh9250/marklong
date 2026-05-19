package com.example.marklong.domain.auth.oauth;

import com.example.marklong.domain.user.domain.OAuthProvider;
import lombok.Getter;

import java.util.Map;

@Getter
public class NaverUserInfo implements Oauth2UserInfo {
    private final Map<String, Object> attributes;

    @SuppressWarnings("unchecked")
    public NaverUserInfo(Map<String, Object> attributes) {
        this.attributes = (Map<String, Object>) attributes.get("response");
    }

    @Override
    public String getProviderId() {
        return (String) attributes.get("id");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getNickname() {
        return (String) attributes.get("nickname");
    }

    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.NAVER;
    }
}
