package com.example.marklong.domain.auth.oauth;

import com.example.marklong.domain.user.domain.OAuthProvider;

public interface Oauth2UserInfo {
    String getProviderId();
    String getEmail();
    String getNickname();
    OAuthProvider getProvider();
}
