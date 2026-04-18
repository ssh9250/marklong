package com.example.marklong.domain.portfolio.domain;

import com.example.marklong.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeHistory extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long portfolioId;

    @Column(nullable = false)
    private String stockCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeType tradeType;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal avgPrice;

    @Column(nullable = false)
    private LocalDateTime tradedAt;

    @Builder
    private TradeHistory(Long portfolioId, String stockCode, TradeType tradeType, BigDecimal quantity, BigDecimal avgPrice, LocalDateTime tradedAt) {
        this.portfolioId = portfolioId;
        this.stockCode = stockCode;
        this.tradeType = tradeType;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
        this.tradedAt = tradedAt;
    }

}
