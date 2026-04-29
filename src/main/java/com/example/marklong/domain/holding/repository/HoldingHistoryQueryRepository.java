package com.example.marklong.domain.holding.repository;

import com.example.marklong.domain.holding.domain.HoldingHistory;
import com.example.marklong.domain.holding.dto.HistorySearchCondition;
import com.example.marklong.domain.holding.dto.HoldingHistoryResponse;
import com.example.marklong.domain.holding.dto.HoldingResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class HoldingHistoryQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<HoldingHistoryResponse> searchHistory(Long userId, HistorySearchCondition cond) {
        return queryFactory.select(Projections.constructor(HoldingHistoryResponse.class,

                ))
    }
}
