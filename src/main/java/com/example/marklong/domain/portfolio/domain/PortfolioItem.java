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
    private Long holdingId;

    @Column(nullable = false)
    private String stockCode;   //  stock domain id

    @Column(nullable = false)
    private BigDecimal allocatedQuantity;

    @Builder
    public PortfolioItem(Long portfolioId, Long holdingId, String stockCode, BigDecimal allocatedQuantity) {
        this.portfolioId = portfolioId;
        this.holdingId = holdingId;
        this.stockCode = stockCode;
        this.allocatedQuantity = allocatedQuantity;
    }

    public void updateAllocation(BigDecimal allocatedQuantity) {
        this.allocatedQuantity = allocatedQuantity;
    }
}
