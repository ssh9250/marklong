package com.example.marklong.domain.portfolio.dto;

import com.example.marklong.domain.holding.domain.Holding;
import com.example.marklong.domain.portfolio.domain.PortfolioItem;
import com.example.marklong.domain.stock.domain.Stock;

public record PortfolioItemViewSource(
        PortfolioItem item,
        Stock stock,
        Holding holding
) {
}
