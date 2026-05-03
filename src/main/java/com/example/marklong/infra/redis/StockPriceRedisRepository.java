package com.example.marklong.infra.redis;

import com.example.marklong.domain.stock.domain.PriceInterval;
import com.example.marklong.domain.stock.domain.StockPrice;
import com.example.marklong.domain.stock.repository.StockPriceRepository;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class StockPriceRedisRepository {
    private final StringRedisTemplate redisTemplate;
    private final StockPriceRepository stockPriceRepository;

    public BigDecimal getCurrentPrice(String stockCode) {
        String cached = redisTemplate.opsForValue().get("price:" + stockCode);

        if (cached != null) {
            return new BigDecimal(cached);
        }

        // 캐시 미스 시 TimescaleDB에서 최신 1분봉 종가 조회
        return stockPriceRepository
                .findTopByStockCodeAndIntervalOrderByTimestampDesc(stockCode, PriceInterval.MINUTE_1)
                .map(StockPrice::getClose)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
    }
}
