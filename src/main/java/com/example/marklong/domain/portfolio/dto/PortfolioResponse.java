package com.example.marklong.domain.portfolio.dto;

import com.example.marklong.domain.portfolio.domain.Portfolio;
import com.example.marklong.domain.stock.domain.Currency;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PortfolioResponse {
    private String name;
    private String description;
    private Currency currency;
    public static PortfolioResponse from(Portfolio portfolio) {
        return PortfolioResponse.builder()
                .name(portfolio.getName())
                .description(portfolio.getDescription())
                .currency(portfolio.getCurrency())
                .build();
    }
}
