package com.example.marklong.infra.client.kis;

import com.example.marklong.domain.stock.domain.Market;
import com.example.marklong.domain.stock.dto.KisCurrentPriceResponse;
import com.example.marklong.domain.stock.dto.KisDailyPriceResponse;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Component
public class KisApiClient {
    private final WebClient kisWebClient;
    private final KisTokenStorage kisTokenStorage;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String MARKET_STATUS_KEY = "kis:market-status";
    private static final Duration MARKET_STATUS_TTL = Duration.ofMinutes(30);

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    public KisApiClient(@Qualifier("kisWebclient") WebClient kisWebClient,
                        KisTokenStorage kisTokenStorage, StringRedisTemplate stringRedisTemplate) {
        this.kisWebClient = kisWebClient;
        this.kisTokenStorage = kisTokenStorage;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private String getOrIssueToken(){
        return kisTokenStorage.getOrIssueToken();
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

    public boolean isMarketOpen() {
        String cached = stringRedisTemplate.opsForValue().get(MARKET_STATUS_KEY);
        if (cached != null) {
            return "true".equals(cached);
        }

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        KisMarketStatusResponse response = kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/chk-holiday")
                        .queryParam("BASS_DT", today)
                        .queryParam("CTX_AREA_NK", "")
                        .queryParam("CTX_AREA_FK", "")
                        .build())
                .header("authorization", "Bearer " + getOrIssueToken())
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "CTCA0903R")
                .retrieve()
                .bodyToMono(KisMarketStatusResponse.class)
                .block();

        if (response == null || response.output() == null || response.output().isEmpty()) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR);
        }

        // opnd_yn: "Y" = 개장일, "N" = 휴장일
        boolean isOpen = "Y".equals(response.output().get(0).opndYn());
        stringRedisTemplate.opsForValue().set(MARKET_STATUS_KEY, String.valueOf(isOpen), MARKET_STATUS_TTL);

        return isOpen;
    }

    public List<KisDailyPriceResponse> getDomesticDailyCandles(String stockCode, LocalDate from, LocalDate to) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");

        KisDomesticDailyResponse response = kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_PERIOD_DIV_CODE", "D")
                        .queryParam("FID_ORG_ADJ_PRC", "0")
                        .build())
                .header("authorization", "Bearer " + getOrIssueToken())
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST01010400")
                .retrieve()
                .bodyToMono(KisDomesticDailyResponse.class)
                .block();

        if (response == null || response.output() == null) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR);
        }

        return response.output().stream()
                .filter(o -> {
                    LocalDate date = LocalDate.parse(o.stckBsopDt(), fmt);
                    return !date.isBefore(from) && !date.isAfter(to);
                })
                .map(o -> new KisDailyPriceResponse(
                        stockCode,
                        LocalDate.parse(o.stckBsopDt(), fmt),
                        new BigDecimal(o.stckOprc()),   // 시가
                        new BigDecimal(o.stckHgpr()),   // 고가
                        new BigDecimal(o.stckLwpr()),   // 저가
                        new BigDecimal(o.stckClpr()),   // 종가
                        Long.parseLong(o.acmlVol())     // 거래량
                ))
                .toList();
    }

    public List<KisDailyPriceResponse> getOverseasDailyCandles(String stockCode, Market market, LocalDate from, LocalDate to) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        String exchgCode = switch (market) {
            case NASDAQ -> "NASD";
            case NYSE   -> "NYSE";
            default     -> throw new BusinessException(ErrorCode.KIS_API_ERROR);
        };

        KisOverseasDailyResponse response = kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/overseas-price/v1/quotations/dailyprice")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", exchgCode)
                        .queryParam("SYMB", stockCode)
                        .queryParam("GUBN", "0")          // 0: 일, 1: 주, 2: 월
                        .queryParam("BYMD", to.format(fmt))
                        .queryParam("MODP", "0")
                        .build())
                .header("authorization", "Bearer " + getOrIssueToken())
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "HHDFS76240000")
                .retrieve()
                .bodyToMono(KisOverseasDailyResponse.class)
                .block();

        if (response == null || response.output2() == null) {
            throw new BusinessException(ErrorCode.KIS_API_ERROR);
        }

        return response.output2().stream()
                .filter(o -> {
                    LocalDate date = LocalDate.parse(o.xymd(), fmt);
                    return !date.isBefore(from) && !date.isAfter(to);
                })
                .map(o -> new KisDailyPriceResponse(
                        stockCode,
                        LocalDate.parse(o.xymd(), fmt),
                        new BigDecimal(o.open()),   // 시가
                        new BigDecimal(o.high()),   // 고가
                        new BigDecimal(o.low()),    // 저가
                        new BigDecimal(o.clos()),   // 종가
                        Long.parseLong(o.tvol())    // 거래량
                ))
                .toList();
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

    private record KisMarketStatusResponse(
            @JsonProperty("output")List<Output> output
            ){
        private record Output(
                @JsonProperty("bass_dt") String bassDt, // 기준일자
                @JsonProperty("opnd_yn") String opndYn // 개장여부 : y/n
        ) {

        }
    }

    private record KisDomesticDailyResponse(
            @JsonProperty("output2") List<Output> output
    ) {
        private record Output(
                @JsonProperty("stck_bsop_dt") String stckBsopDt,  // 영업일자
                @JsonProperty("stck_oprc")    String stckOprc,    // 시가
                @JsonProperty("stck_hgpr")    String stckHgpr,    // 고가
                @JsonProperty("stck_lwpr")    String stckLwpr,    // 저가
                @JsonProperty("stck_clpr")    String stckClpr,    // 종가
                @JsonProperty("acml_vol")     String acmlVol      // 거래량
        ) {}
    }

    private record KisOverseasDailyResponse(
            @JsonProperty("output2") List<Output> output2
    ) {
        private record Output(
                @JsonProperty("xymd") String xymd,   // 일자
                @JsonProperty("open") String open,   // 시가
                @JsonProperty("high") String high,   // 고가
                @JsonProperty("low")  String low,    // 저가
                @JsonProperty("clos") String clos,   // 종가
                @JsonProperty("tvol") String tvol    // 거래량
        ) {}
    }
}