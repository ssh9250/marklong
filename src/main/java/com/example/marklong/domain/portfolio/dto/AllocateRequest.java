package com.example.marklong.domain.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AllocateRequest {
    @NotBlank
    private String stockCode;
    @NotBlank
    private BigDecimal quantity;
}
