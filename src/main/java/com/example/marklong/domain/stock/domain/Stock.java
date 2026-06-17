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
    // TODO: 해외 종목은 KIS API의 SYMB 파라미터가 ticker를 그대로 요구하므로, stockCode에 ticker(AAPL 등)를 저장
    //       CSV overseas 파일의 stock_code 컬럼을 제거하고 ticker 값을 그대로 stockCode로 사용할 것
    private String stockCode; // 국내: 005930 / 해외: AAPL

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;  //  KOSPI, NASDAQ 등

    @Enumerated(EnumType.STRING)
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
