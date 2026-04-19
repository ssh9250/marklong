package com.example.marklong.domain.stock.repository;

import com.example.marklong.domain.stock.domain.PriceInterval;
import com.example.marklong.domain.stock.domain.StockPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {
    List<StockPrice> findAllByStockCodeAndIntervalOrderByTimestampDesc(String stockCode, PriceInterval interval);
    Optional<StockPrice> findTopByStockCodeAndIntervalOrderByTimestampDesc(String stockCode, PriceInterval interval);
}
