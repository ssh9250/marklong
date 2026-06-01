package com.example.marklong.infra.client.kis;

import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class KisAuthClient {
    private final WebClient kisWebClient;

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    public KisAuthClient(@Qualifier("kisWebClient") WebClient kisWebClient) {
        this.kisWebClient = kisWebClient;
    }

    public String requestNewAccessToken() {
        KisTokenStorage response = kisWebClient.post()
                .uri("/oauth2/tokenP")
                .bodyValue(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", appKey,
                        "appsecret", appSecret
                ))
                .retrieve()
                .bodyToMono(KisTokenStorage.class)
                .block();

        if (response == null || response.getAccessToken() == null) {
            throw new BusinessException(ErrorCode.KIS_AUTH_FAILED);
        }
        return response.getAccessToken();
    }
}
