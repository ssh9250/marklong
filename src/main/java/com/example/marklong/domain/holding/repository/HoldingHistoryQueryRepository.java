package com.example.marklong.domain.holding.repository;

import com.example.marklong.domain.holding.domain.TradeType;
import com.example.marklong.domain.holding.dto.HistorySearchCondition;
import com.example.marklong.domain.holding.dto.HoldingHistoryResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.example.marklong.domain.holding.domain.QHoldingHistory.holdingHistory;
import static org.springframework.util.StringUtils.hasText;

@Repository
@RequiredArgsConstructor
public class HoldingHistoryQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<HoldingHistoryResponse> searchHistory(Long userId, HistorySearchCondition cond) {
        return queryFactory
                .select(Projections.constructor(HoldingHistoryResponse.class,
                        holdingHistory.id, holdingHistory.stockCode, holdingHistory.stockName, holdingHistory.quantity, holdingHistory.price, holdingHistory.memo))
                .from(holdingHistory)
                .where(
                        userIdEq(userId),
                        stockCodeContains(cond.getStockCode()),
                        stockNameContains(cond.getStockName()),
                        memoContains(cond.getMemo()),
                        tradedAtFrom(cond.getTradedAtFrom()),
                        tradedAtTo(cond.getTradedAtTo()),
                        tradeTypeEq(cond.getTradeType()),
                        isProfit(cond.getIsProfit())
                )
                .orderBy(holdingHistory.tradedAt.desc())
                .fetch();
    }

    private BooleanExpression userIdEq(Long userId) {
        return holdingHistory.userId.eq(userId);
    }

    private BooleanExpression stockCodeContains(String stockCode) {
        return hasText(stockCode) ? holdingHistory.stockCode.containsIgnoreCase(stockCode) : null;
    }

    private BooleanExpression stockNameContains(String stockName) {
        return hasText(stockName) ? holdingHistory.stockName.containsIgnoreCase(stockName) : null;
    }

    private BooleanExpression memoContains(String memo) {
        return hasText(memo) ? holdingHistory.memo.containsIgnoreCase(memo) : null;
    }

    private BooleanExpression tradedAtFrom(LocalDateTime from) {
        return from == null ? null : holdingHistory.tradedAt.goe(from);
    }

    private BooleanExpression tradedAtTo(LocalDateTime to) {
        return to == null ? null : holdingHistory.tradedAt.lt(to.plusDays(1));
        // less then + 1 => 선택한 날짜의 23:59:59 까지 모두 포함
    }

    private BooleanExpression tradeTypeEq(TradeType tradeType) {
        return tradeType == null ? null : holdingHistory.tradeType.eq(tradeType);
    }

    private BooleanExpression isProfit(Boolean isProfit) {
        if (isProfit == null) {
            return null;
        }

        return isProfit
                ? holdingHistory.realizedProfit.gt(BigDecimal.ZERO)
                : holdingHistory.realizedProfit.loe(BigDecimal.ZERO);
    }
}
