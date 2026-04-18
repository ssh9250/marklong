package com.example.marklong.domain.stock.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String stockCode;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;

    @Column(nullable = false)
    private BigDecimal close;

    private Long volume;

    @Enumerated(EnumType.STRING)
    private PriceInterval interval;

    @Builder
    private StockPrice(String stockCode, LocalDateTime timestamp,
                       BigDecimal open, BigDecimal high, BigDecimal low,
                       BigDecimal close, Long volume, PriceInterval interval) {
        this.stockCode = stockCode;
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.interval = interval;
    }
}
