package com.example.marklong.domain.portfolio.dto;

import com.example.marklong.domain.holding.domain.Holding;
import com.example.marklong.domain.portfolio.domain.PortfolioItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Builder
public class PortfolioItemResponse {
    private Long id;
    private String stockCode;
    private String stockName;

    private BigDecimal quantity;
    private BigDecimal avgPrice;    //  평균 매입가
    private BigDecimal currentPrice; // 현재가

    private BigDecimal investment;  //  투자금액
    private BigDecimal valuation;   //  평가금
    private BigDecimal profit;      //  손익
    private BigDecimal returnRate;  //  수익률

    public static PortfolioItemResponse of(PortfolioItem item, String name, Holding holding, BigDecimal currentPrice) {
        BigDecimal investment = item.getAllocatedQuantity().multiply(holding.getAvgPrice());
        BigDecimal valuation = currentPrice.multiply(item.getAllocatedQuantity());
        BigDecimal profit = valuation.subtract(investment);
        BigDecimal returnRate = investment.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : profit.divide(investment, 4, RoundingMode.HALF_UP)
                  .multiply(BigDecimal.valueOf(100));

        return PortfolioItemResponse.builder()
                .id(item.getId())
                .stockCode(item.getStockCode())
                .stockName(name)
                .quantity(item.getAllocatedQuantity())
                .avgPrice(holding.getAvgPrice())
                .currentPrice(currentPrice)
                .investment(investment)
                .valuation(valuation)
                .profit(profit)
                .returnRate(returnRate)
                .build();
    }
}
