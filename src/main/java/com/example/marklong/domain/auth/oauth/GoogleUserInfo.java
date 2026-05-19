package com.example.marklong.domain.auth.oauth;

import com.example.marklong.domain.user.domain.OAuthProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@Getter
@RequiredArgsConstructor
public class GoogleUserInfo implements Oauth2UserInfo {
    private final Map<String, Object> attributes;

    @Override
    public String getProviderId() {
        return (String) attributes.get("sub");
    }
    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getNickname() {
        return (String) attributes.get("name");
    }
    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.GOOGLE;
    }
}
