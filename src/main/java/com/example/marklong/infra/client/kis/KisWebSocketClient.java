package com.example.marklong.infra.client.kis;

import com.example.marklong.domain.stock.repository.StockPriceRedisRepository;
import com.example.marklong.infra.kafka.dto.StockAlertEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
@RequiredArgsConstructor
public class KisWebSocketClient {
    private final StockPriceRedisRepository stockPriceRedisRepository;
    private final KafkaTemplate<String, StockAlertEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private WebSocketSession currentSession;
    private final ReentrantLock sessionLock = new ReentrantLock();

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    // ── 연결 ─────────────────────────────────────────────────
    public void connect(String approvalKey) {
        sessionLock.lock();
        try {
            closeIfOpen();

            URI uri = URI.create("ws://ops.koreainvestment.com:21000");
            StandardWebSocketClient client = new StandardWebSocketClient();

            client.execute(new TextWebSocketHandler() {

                            @Override
                            public void afterConnectionEstablished(WebSocketSession session) {
                                currentSession = session;
                                log.info("[KIS WS] 연결 성공");

                                // 서버 재시작 시 기존 구독 종목 복원
                                Set<String> previouslySub = stockPriceRedisRepository.getSubscribedCodes();
                                if (!previouslySub.isEmpty()) {
                                    log.info("[KIS WS] 구독 복원: {}개 종목", previouslySub.size());
                                    previouslySub.forEach(code -> sendSubscribeMessage(session, code, approvalKey, true));
                                }
                            }

                            @Override
                            public void handleTextMessage(WebSocketSession session, TextMessage message) {
                                handleIncomingMessage(message.getPayload());
                            }

                            @Override
                            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                                log.warn("[KIS WS] 연결 종료: {}", status);
                                currentSession = null;
                            }

                            @Override
                            public void handleTransportError(WebSocketSession session, Throwable ex) {
                                log.error("[KIS WS] 전송 오류", ex);
                            }
                        }, new WebSocketHttpHeaders(), uri);
        } finally {
            sessionLock.unlock();
        }
    }

    // ── 종목 구독 ─────────────────────────────────────────────
    public void subscribe(String stockCode, String approvalKey) {
        if (stockPriceRedisRepository.isSubscribed(stockCode)) {
            log.debug("[KIS WS] 이미 구독 중: {}", stockCode);
            return;
        }
        if (!isConnected()) {
            log.warn("[KIS WS] 세션 없음 — 구독 불가: {}", stockCode);
            return;
        }
        sendSubscribeMessage(currentSession, stockCode, approvalKey, true);
        stockPriceRedisRepository.addSubscription(stockCode);
        log.info("[KIS WS] 구독 추가: {}", stockCode);
    }

    // ── 종목 구독 해제 ────────────────────────────────────────
    public void unsubscribe(String stockCode, String approvalKey) {
        if (!stockPriceRedisRepository.isSubscribed(stockCode)) {
            return;
        }
        if (!isConnected()) {
            stockPriceRedisRepository.removeSubscription(stockCode);
            return;
        }
        sendSubscribeMessage(currentSession, stockCode, approvalKey, false);
        stockPriceRedisRepository.removeSubscription(stockCode);
        log.info("[KIS WS] 구독 해제: {}", stockCode);
    }

    // ── 메시지 전송 (구독/해제 공통) ─────────────────────────
    private void sendSubscribeMessage(WebSocketSession session, String stockCode,
                                      String approvalKey, boolean subscribe) {
        try {
            Map<String, Object> header = Map.of(
                    "approval_key", approvalKey,
                    "custtype", "P",
                    "tr_type", subscribe ? "1" : "2",   // 1=구독, 2=해제
                    "content-type", "utf-8"
            );
            Map<String, Object> input = Map.of(
                    "tr_id", "H0STCNT0",                // 국내 실시간 체결가
                    "tr_key", stockCode
            );
            Map<String, Object> body = Map.of("input", input);
            Map<String, Object> payload = Map.of("header", header, "body", body);

            String json = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("[KIS WS] 메시지 전송 실패: stockCode={}", stockCode, e);
        }
    }

    // ── 수신 메시지 처리 ──────────────────────────────────────
    private void handleIncomingMessage(String payload) {
        // KIS WS 응답은 두 가지 포맷:
        // 1) JSON — 연결확인·오류·PINGPONG
        // 2) 파이프(|) 구분 — 실시간 시세 데이터

        if (payload.startsWith("{")) {
            handleJsonMessage(payload);
        } else {
            handlePipeMessage(payload);
        }
    }

    private void handleJsonMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String trId = root.path("header").path("tr_id").asText();

            if ("PINGPONG".equals(trId)) {
                // PINGPONG 미응답 시 서버가 연결을 끊으므로 필수
                currentSession.sendMessage(new TextMessage(payload));
                log.debug("[KIS WS] PINGPONG 응답");
                return;
            }

            String trKey = root.path("body").path("output").path("tr_key").asText();
            log.info("[KIS WS] JSON 응답 — tr_id={}, tr_key={}", trId, trKey);

        } catch (Exception e) {
            log.error("[KIS WS] JSON 파싱 실패: {}", payload, e);
        }
    }

    private void handlePipeMessage(String payload) {
        // KIS 파이프 포맷: {암호화여부}|{tr_id}|{데이터수}|{데이터}
        // 예: 0|H0STCNT0|001|005930^093045^70400^...
        String[] parts = payload.split("\\|", 4);
        if (parts.length < 4) {
            log.warn("[KIS WS] 파이프 파싱 불가: {}", payload);
            return;
        }

        String encrypted = parts[0];  // 0=평문, 1=암호화
        String trId      = parts[1];
        // parts[2] = 데이터 건수
        String data      = parts[3];

        if ("1".equals(encrypted)) {
            // 암호화 데이터는 현재 미처리 (approval key 발급 시 암호화 설정에 따라 결정)
            log.debug("[KIS WS] 암호화 데이터 수신 — 미처리");
            return;
        }

        if ("H0STCNT0".equals(trId)) {
            parseAndProcessDomesticPrice(data);
        }
    }

    // ── 국내 실시간 체결가 파싱 ───────────────────────────────
    // KIS H0STCNT0 필드 순서 (^로 구분):
    // [0]종목코드 [2]체결시간 [2]현재가 [4]전일대비 [5]등락률 ...
    private void parseAndProcessDomesticPrice(String data) {
        String[] fields = data.split("\\^");
        if (fields.length < 6) {
            log.warn("[KIS WS] 체결가 데이터 필드 부족: {}", data);
            return;
        }

        try {
            String stockCode = fields[0];
            BigDecimal currentPrice = new BigDecimal(fields[2]);
            BigDecimal changeAmount = new BigDecimal(fields[4]);
            BigDecimal changeRate   = new BigDecimal(fields[5]);

            // 1) Redis 현재가 갱신
            stockPriceRedisRepository.saveCurrentPrice(stockCode, currentPrice);

            // 2) 알림 조건 체크 → Kafka 발행
            checkAndPublishAlert(stockCode, currentPrice, changeRate);

        } catch (NumberFormatException e) {
            log.error("[KIS WS] 숫자 파싱 실패: {}", data, e);
        }
    }

    // ── 알림 조건 체크 ────────────────────────────────────────
    private void checkAndPublishAlert(String stockCode, BigDecimal currentPrice,
                                      BigDecimal changeRate) {
        // 등락률 ±3% 이상이면 알림 발행 (임계값은 추후 사용자 설정으로 교체 가능)
        BigDecimal threshold = new BigDecimal("3.0");

        if (changeRate.abs().compareTo(threshold) >= 0) {
            StockAlertEvent event = StockAlertEvent.builder()
                    .stockCode(stockCode)
//                    .currentPrice(currentPrice)
//                    .changeRate(changeRate)
//                    .alertType(changeRate.compareTo(BigDecimal.ZERO) > 0
//                            ? AlertType.PRICE_SURGE
//                            : AlertType.PRICE_DROP)
                    .build();

            kafkaTemplate.send("stock.alert", stockCode, event);
            log.info("[KIS WS] 알림 발행: stockCode={}, changeRate={}", stockCode, changeRate);
        }
    }

    // ── 유틸 ─────────────────────────────────────────────────
    public boolean isConnected() {
        return currentSession != null && currentSession.isOpen();
    }

    private void closeIfOpen() {
        if (isConnected()) {
            try {
                currentSession.close();
            } catch (IOException e) {
                log.warn("[KIS WS] 기존 세션 종료 실패", e);
            }
        }
    }
}