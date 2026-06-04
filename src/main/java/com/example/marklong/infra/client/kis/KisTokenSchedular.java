package com.example.marklong.infra.client.kis;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KisTokenSchedular {
    private final KisTokenStorage kisTokenStorage;
    private final KisAuthClient kisAuthClient;
    private final KisWebSocketClient kisWebSocketClient;
    private String currentApprovalKey;

    // 매일 새벽 5시에 KIS 접근 토큰 갱신 API 호출
    @Scheduled(cron = "0 0 5 * * *")
    public void refreshAccessToken() {
        // 1. KIS 토큰 발급 REST API 호출
        String newCode = kisAuthClient.requestNewAccessToken();

        // 2. Redis 또는 DB에 저장 (유효기간 24시간 설정)
        kisTokenStorage.saveAccessToken(newCode);
    }

    // 매일 오전 8시 장 시작 전에 웹소켓 접속키 갱신
    @Scheduled(cron = "0 0 8 * * *")
    public void refreshApprovalKey() {
        // 1. KIS로부터 approval_key 발급 받기
        this.currentApprovalKey = kisAuthClient.requestApprovalKey();
        // 2. 기존에 연결되어 있던 웹소켓 세션이 있다면 안전하게 종료하고,
        //    새로 발급받은 키를 사용해 KIS 웹소켓 서버에 새롭게 커넥션을 수립(Connect)합니다.
        kisWebSocketClient.connect(this.currentApprovalKey);
    }
}
