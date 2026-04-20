package com.example.marklong.domain.portfolio.dto;

import com.example.marklong.domain.portfolio.domain.Portfolio;
import com.example.marklong.domain.stock.domain.Currency;
import com.example.marklong.domain.stock.domain.Stock;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Getter
@Builder
public class PortfolioResponse {
    private String name;
    private String description;
    private Currency currency;

    private BigDecimal totalInvestment; // 총 매입금
    private BigDecimal totalValuation;  // 총 평가금
    private BigDecimal totalProfit;     // 총 손익
    private BigDecimal totalReturnRate; // 총 수익률

    private LocalDateTime createdAt;


    public static PortfolioResponse of(Portfolio portfolio, BigDecimal totalInvestment, BigDecimal totalValuation) {
        BigDecimal profit = totalValuation.subtract(totalInvestment);
        BigDecimal returnRate = totalInvestment.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO : profit.divide(totalInvestment, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100));

        return PortfolioResponse.builder()
                .name(portfolio.getName())
                .description(portfolio.getDescription())
                .currency(portfolio.getCurrency())
                .totalInvestment(totalInvestment)
                .totalValuation(totalValuation)
                .totalProfit(profit)
                .totalReturnRate(returnRate)
                .createdAt(portfolio.getCreatedAt())
                .build();
    }
}