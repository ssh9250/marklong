package com.example.marklong.infra.client.kis;

import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
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
        KisTokenResponse response = kisWebClient.post()
                .uri("/oauth2/tokenP")
                .bodyValue(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", appKey,
                        "appsecret", appSecret
                ))
                .retrieve()
                .bodyToMono(KisTokenResponse.class)
                .block();

        if (response == null || response.accessToken() == null) {
            throw new BusinessException(ErrorCode.KIS_AUTH_FAILED);
        }
        return response.accessToken();
    }

    public String requestApprovalKey() {
        KisApprovalResponse response = kisWebClient.post()
                .uri("/oauth2/Approval")
                .bodyValue(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", appKey,
                        "secretkey", appSecret
                ))
                .retrieve()
                .bodyToMono(KisApprovalResponse.class)
                .block();

        if (response == null || response.approvalKey() == null) {
            throw new BusinessException(ErrorCode.KIS_AUTH_FAILED);
        }
        return response.approvalKey();
    }

    // inner record
    private record KisTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn) {}

    private record KisApprovalResponse(
            @JsonProperty("approval_key") String approvalKey
    ) {
    }
}
