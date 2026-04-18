package com.example.marklong.domain.portfolio.domain;

import com.example.marklong.domain.stock.domain.Currency;
import com.example.marklong.global.entity.BaseEntity;
import com.example.marklong.global.entity.SoftDeleteEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portfolios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Builder
    private Portfolio(Long userId, String name, String description, Currency currency) {
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.currency = currency;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
