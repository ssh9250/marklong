package com.example.marklong.domain.portfolio.repository;

import com.example.marklong.domain.portfolio.dto.PortfolioItemViewSource;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.marklong.domain.holding.domain.QHolding.holding;
import static com.example.marklong.domain.portfolio.domain.QPortfolioItem.portfolioItem;
import static com.example.marklong.domain.stock.domain.QStock.stock;

@Repository
@RequiredArgsConstructor
public class PortfolioQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<PortfolioItemViewSource> buildItemResponseSource(Long portfolioId) {
        return queryFactory.select(Projections.constructor(PortfolioItemViewSource.class,
                        portfolioItem, stock, holding))
                .from(portfolioItem)
                .leftJoin(stock).on(stock.stockCode.eq(portfolioItem.stockCode))
                .leftJoin(holding).on(holding.id.eq(portfolioItem.holdingId))
                .where(
                        portfolioItem.portfolioId.eq(portfolioId),
                        portfolioItem.deletedAt.isNull(),
//                        stock.active.isTrue(),
                        holding.deletedAt.isNull()
                )
                .fetch();
    }
}
