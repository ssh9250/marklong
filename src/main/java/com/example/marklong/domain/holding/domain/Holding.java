package com.example.marklong.domain.holding.domain;

import com.example.marklong.global.entity.SoftDeleteEntity;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "holdings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holding extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String stockCode;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal totalQuantity;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal avgPrice;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal allocatedQuantity;

    @Builder
    private Holding(Long userId, String stockCode, BigDecimal totalQuantity, BigDecimal avgPrice) {
        this.userId = userId;
        this.stockCode = stockCode;
        this.totalQuantity = totalQuantity;
        this.avgPrice = avgPrice;
        this.allocatedQuantity = BigDecimal.ZERO;
    }

    public void buy(BigDecimal quantity, BigDecimal price) {
        BigDecimal totalCost = this.avgPrice.multiply(this.totalQuantity)
                .add(quantity.multiply(price));
        this.totalQuantity = this.totalQuantity.add(quantity);
        this.avgPrice = totalCost.divide(this.totalQuantity, 4,  RoundingMode.HALF_UP);
    }

    public void sell(BigDecimal quantity) {
        if (this.totalQuantity.compareTo(quantity) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY);
        }
        this.totalQuantity = this.totalQuantity.subtract(quantity);
        if (this.totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            this.delete();
        }
    }

    public void allocate(BigDecimal quantity) {
        BigDecimal unallocated = this.totalQuantity.subtract(this.allocatedQuantity);
        if (unallocated.compareTo(quantity) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY);
        }
        this.allocatedQuantity = this.allocatedQuantity.add(quantity);
    }

    public void deallocate(BigDecimal quantity) {
        if (this.allocatedQuantity.compareTo(quantity) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY);
        }
        this.allocatedQuantity = this.allocatedQuantity.subtract(quantity);
    }

    public BigDecimal getUnallocatedQuantity() {
        return this.totalQuantity.subtract(this.allocatedQuantity);
    }
}
