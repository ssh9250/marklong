package com.example.marklong.domain.auth.oauth;

import com.example.marklong.domain.auth.service.RefreshTokenService;
import com.example.marklong.domain.user.domain.OAuthProvider;
import com.example.marklong.domain.user.domain.Role;
import com.example.marklong.domain.user.domain.User;
import com.example.marklong.domain.user.repository.UserRepository;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthProvider provider = OAuthProvider.from(registrationId);

        Oauth2UserInfo userInfo =
                Oauth2UserInfoFactory.getOauth2UserInfo(provider, oAuth2User.getAttributes());

        String email = userInfo.getEmail();
        if (email == null) {
            throw new BusinessException(ErrorCode.OAUTH_EMAIL_NOT_FOUND);
        }

        User user = userRepository.findByOauthIdAndProviderAndDeletedAtIsNull(userInfo.getProviderId(), provider)
                .orElseGet(() -> userRepository.findByEmailAndDeletedAtIsNull(email)
                        .orElseGet(() -> registerNewUser(userInfo)));

        return new CustomOauth2User(user);
    }

    private User registerNewUser(Oauth2UserInfo userInfo) {
        User user = User.builder()
                .email(userInfo.getEmail())
                .nickname(userInfo.getNickname())
                .role(Role.USER)
                .provider(userInfo.getProvider())
                .password(null)
                .oauthId(userInfo.getProviderId())
                .build();
        userRepository.save(user);
        return user;
    }
}
