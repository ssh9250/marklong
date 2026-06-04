package com.example.marklong.infra.client.kis;

import com.example.marklong.domain.stock.domain.Market;
import com.example.marklong.domain.stock.dto.KisCurrentPriceResponse;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;


@Component
public class KisApiClient {
    private final WebClient kisWebClient;
    private final KisTokenStorage kisTokenStorage;
    private final KisAuthClient kisAuthClient;

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    public KisApiClient(@Qualifier("kisWebclient") WebClient kisWebClient,
                        KisTokenStorage kisTokenStorage,
                        KisAuthClient kisAuthClient) {
        this.kisWebClient = kisWebClient;
        this.kisTokenStorage = kisTokenStorage;
        this.kisAuthClient = kisAuthClient;
    }

    private String getOrIssueToken(){
        String token = kisTokenStorage.getAccessToken();
        if (token == null){
            token = kisAuthClient.requestNewAccessToken();
            kisTokenStorage.saveAccessToken(token);
        }
        return token;
    }

    public KisCurrentPriceResponse getCurrentPrice(String stockCode, Market market) {
        return switch (market) {
            case KOSPI, KOSDAQ -> getDomesticCurrentPrice(stockCode, market);
            case NASDAQ, NYSE -> getOverseasCurrentPrice(stockCode, market);
        };
    }

    private KisCurrentPriceResponse getDomesticCurrentPrice(String stockCode, Market market) {
        KisDomesticPriceResponse response = kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .build())
                .header("authorization", "Bearer " + getOrIssueToken())
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST01010100")
                .retrieve()
                .bodyToMono(KisDomesticPriceResponse.class)
                .block();

        if (response == null || response.output() == null) {
            throw new BusinessException(ErrorCode.KIS_AUTH_FAILED);
        }

        KisDomesticPriceResponse.Output output = response.output();
        return new KisCurrentPriceResponse(
                stockCode,
                new BigDecimal(output.stckPrpr()),    // 현재가
                new BigDecimal(output.prdyVrss()),    // 전일 대비
                new BigDecimal(output.prdyCtrt()),    // 등락률
                Long.parseLong(output.acmlVol()),     // 누적 거래량
                market
        );
    }

    private KisCurrentPriceResponse getOverseasCurrentPrice(String stockCode, Market market) {
        String exchgCode = switch (market) {
            case NASDAQ -> "NASD";
            case NYSE -> "NYSE";
            default -> throw new BusinessException(ErrorCode.KIS_AUTH_FAILED);
        };

        KisOverseasPriceResponse response = kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/overseas-price/v1/quotations/price")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", exchgCode)
                        .queryParam("SYMB", stockCode)
                        .build())
                .header("authorization", "Bearer " + getOrIssueToken())
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "HHDFS00000300")
                .retrieve()
                .bodyToMono(KisOverseasPriceResponse.class)
                .block();

        if (response == null || response.output() == null) {
            throw new BusinessException(ErrorCode.KIS_AUTH_FAILED);
        }

        KisOverseasPriceResponse.Output output = response.output();
        return new KisCurrentPriceResponse(
                stockCode,
                new BigDecimal(output.last()),        // 현재가
                new BigDecimal(output.diff()),        // 전일 대비
                new BigDecimal(output.rate()),        // 등락률
                Long.parseLong(output.tvol()),        // 거래량
                market
        );
    }

    // KIS 국내 응답 구조
    private record KisDomesticPriceResponse(
            @JsonProperty("output") Output output
    ) {
        private record Output(
                @JsonProperty("stck_prpr") String stckPrpr,   // 현재가
                @JsonProperty("prdy_vrss") String prdyVrss,   // 전일 대비
                @JsonProperty("prdy_ctrt") String prdyCtrt,   // 등락률
                @JsonProperty("acml_vol")  String acmlVol     // 누적 거래량

        ) {
        }
    }

    // KIS 해외 응답 구조
    private record KisOverseasPriceResponse(
            @JsonProperty("output") Output output
    ) {
        private record Output(
                @JsonProperty("last") String last,   // 현재가
                @JsonProperty("diff") String diff,   // 전일 대비
                @JsonProperty("rate") String rate,   // 등락률
                @JsonProperty("tvol") String tvol    // 거래량
        ) {}
    }
}
