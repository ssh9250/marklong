package com.example.marklong.domain.portfolio.dto;

import com.example.marklong.domain.portfolio.domain.PortfolioItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class PortfolioDetailResponse {
    private PortfolioResponse summary;
    private List<PortfolioItemResponse> items;

    public static PortfolioDetailResponse of(PortfolioResponse summary, List<PortfolioItemResponse> items) {
        return PortfolioDetailResponse.builder()
                .summary(summary)
                .items(items)
                .build();
    }
}
