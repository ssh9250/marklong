package com.example.marklong.domain.stock.repository;

import com.example.marklong.domain.stock.domain.Market;
import com.example.marklong.domain.stock.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, String> {
    List<Stock> findAllByActiveTrue();
    List<Stock> findAllByMarket(Market market);
    Optional<Stock> findByStockCodeAndActiveTrue(String stockCode);
}
