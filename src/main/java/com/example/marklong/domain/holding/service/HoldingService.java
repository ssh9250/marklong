package com.example.marklong.domain.holding.service;

import com.example.marklong.domain.holding.domain.Holding;
import com.example.marklong.domain.holding.domain.HoldingHistory;
import com.example.marklong.domain.holding.domain.TradeType;
import com.example.marklong.domain.holding.dto.*;
import com.example.marklong.domain.holding.repository.HoldingHistoryRepository;
import com.example.marklong.domain.holding.repository.HoldingRepository;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class HoldingService {
    private final HoldingRepository holdingRepository;
    private final HoldingHistoryRepository holdingHistoryRepository;

    public HoldingResponse buy(Long userId, HoldingBuyRequest request) {

        Holding holding = holdingRepository
                .findByUserIdAndStockCodeAndDeletedAtIsNull(userId, request.getStockCode())
                .map(existing -> {
                    existing.buy(request.getQuantity(), request.getPrice());
                    return existing;
                })
                .orElseGet(() -> holdingRepository.save(
                        Holding.builder()
                                .userId(userId)
                                .stockCode(request.getStockCode())
                                .totalQuantity(request.getQuantity())
                                .avgPrice(request.getPrice())
                                .build()
                ));

        holdingHistoryRepository.save(
                HoldingHistory.builder()
                        .userId(userId)
                        .stockCode(request.getStockCode())
                        .tradeType(TradeType.BUY)
                        .quantity(request.getQuantity())
                        .price(request.getPrice())
                        .tradedAt(LocalDateTime.now())
                        .memo(request.getMemo())
                        .build());
        return HoldingResponse.from(holding);
    }

    // 매도 실행 시 portfolioItem은 건드리지 않음
    // todo: 매도로 인해 보유 자산과 포트폴리오에 속한 종목 오차 발생 가능 -> 배분 수량 합계 > holding 시 경고 발행
    // or 비율적으로 자동 조정 or 매도 시 포트폴리오 지정
    public HoldingResponse sell(Long userId, HoldingSellRequest request) {
        Holding holding = getHoldingOrThrow(userId, request.getStockCode());

        holding.sell(request.getQuantity());

        holdingHistoryRepository.save(
                HoldingHistory.builder()
                        .userId(userId)
                        .stockCode(request.getStockCode())
                        .tradeType(TradeType.SELL)
                        .quantity(request.getQuantity())
                        .price(request.getPrice())
                        .avgPriceAtTrade(holding.getAvgPrice())
                        .tradedAt(LocalDateTime.now())
                        .memo(request.getMemo())
                        .build()
        );
        return HoldingResponse.from(holding);
    }

    @Transactional(readOnly = true)
    public List<HoldingResponse> getMyHoldings(Long userId) {
        return holdingRepository.findAllByUserIdAndDeletedAtIsNull(userId)
                .stream()
                .map(HoldingResponse::from)
                .toList();
    }

    // todo: queryDSL로 condition을 통해 동적 검색 예정
    // cond = null일 경우 모든 종목(code) 모든 거래방식(sell, buy) 모두 호출
    // 종목별, 거래방식별, memo 유무 별 filter
    @Transactional(readOnly = true)
    public List<HoldingHistoryResponse> searchHistory(Long userId, HistorySearchCondition condition) {
        return null;
    }

    private Holding getHoldingOrThrow(Long userId, String stockCode) {
        return holdingRepository.findByUserIdAndStockCodeAndDeletedAtIsNull(userId, stockCode)
                .orElseThrow(()->new BusinessException(ErrorCode.HOLDING_NOT_FOUND));
    }
}
