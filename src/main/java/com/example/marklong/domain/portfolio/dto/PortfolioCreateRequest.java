package com.example.marklong.domain.portfolio.dto;

import com.example.marklong.domain.stock.domain.Currency;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PortfolioCreateRequest {
    @NotBlank
    private String name;
    private String description;
    @NotBlank
    private Currency currency;
}
