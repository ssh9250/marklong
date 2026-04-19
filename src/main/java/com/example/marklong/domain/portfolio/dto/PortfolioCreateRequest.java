package com.example.marklong.domain.portfolio.dto;

import com.example.marklong.domain.stock.domain.Currency;
import lombok.Getter;

@Getter
public class PortfolioCreateRequest {
    private String name;
    private String description;
    private Currency currency;
}
