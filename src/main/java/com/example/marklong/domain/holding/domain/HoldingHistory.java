package com.example.marklong.domain.holding.domain;

import com.example.marklong.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "holding_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldingHistory extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String stockCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeType tradeType;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    private String memo;

    @Column(nullable = false)
    private LocalDateTime tradedAt;

    @Builder
    private  HoldingHistory(Long userId, String stockCode, TradeType tradeType, BigDecimal quantity, BigDecimal price, String memo, LocalDateTime tradedAt) {
        this.userId = userId;
        this.stockCode = stockCode;
        this.tradeType = tradeType;
        this.quantity = quantity;
        this.price = price;
        this.memo = memo;
        this.tradedAt = tradedAt;
    }
}
