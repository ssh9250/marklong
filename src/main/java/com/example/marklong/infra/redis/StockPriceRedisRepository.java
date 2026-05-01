package com.example.marklong.infra.redis;

import com.example.marklong.domain.stock.domain.StockPrice;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;

import java.math.BigDecimal;

public class StockPriceRedisRepository {
    public BigDecimal getCurrentPrice(String stockCode){
        String cached = redisTemplate.opsForValue().get("price:" + stockCode);

        if (cached != null) {
            return new BigDecimal(cached);
        }

        // 캐시 미스 시 timescale db에서 최신값 가져오기
        return stockPriceRepository.findLatestByStockCode(stockCode)
                .map(StockPrice::getClose)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
    }
}
