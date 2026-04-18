package com.example.marklong.domain.portfolio.domain;

import com.example.marklong.global.entity.BaseEntity;
import com.example.marklong.global.entity.SoftDeleteEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "portfolio_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioItem extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long portfolioId;

    @Column(nullable = false)
    private String stockCode;   //  stock domain id

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal avgPrice;

    @Builder
    private PortfolioItem(Long portfolioId, String stockCode, BigDecimal quantity, BigDecimal avgPrice) {
        this.portfolioId = portfolioId;
        this.stockCode = stockCode;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
    }

    public void updateHolding(BigDecimal newQuantity, BigDecimal newAvgPrice) {
        this.quantity = newQuantity;
        this.avgPrice = newAvgPrice;
    }

}
