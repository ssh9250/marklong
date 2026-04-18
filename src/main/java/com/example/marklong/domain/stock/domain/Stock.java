package com.example.marklong.domain.stock.domain;

import com.example.marklong.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseEntity {
    @Id
    private String stockCode; // 005930, AAPL 등

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;  //  KOSPI, NASDAQ 등

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;  //  KRW, USD 등

    private String sector;
    private boolean active = true;

    @Builder
    private Stock(String stockCode, String name, Market market,
                  Currency currency, String sector) {
        this.stockCode = stockCode;
        this.name = name;
        this.market = market;
        this.currency = currency;
        this.sector = sector;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }



}
