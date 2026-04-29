package com.example.marklong.domain.holding.dto;

import com.example.marklong.domain.holding.domain.TradeType;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class HistorySearchCondition {
    private String stockCode;
    private String stockName;
    private String memo;
    private LocalDateTime tradedAtFrom;
    private LocalDateTime tradedAtTo;
    private TradeType tradeType;
    private Boolean isProfit;
}
