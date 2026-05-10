package com.example.marklong.domain.holding.service;

import com.example.marklong.domain.holding.domain.Holding;
import com.example.marklong.domain.holding.domain.HoldingHistory;
import com.example.marklong.domain.holding.domain.TradeType;
import com.example.marklong.domain.holding.dto.*;
import com.example.marklong.domain.holding.repository.HoldingHistoryQueryRepository;
import com.example.marklong.domain.holding.repository.HoldingHistoryRepository;
import com.example.marklong.domain.holding.repository.HoldingRepository;
import com.example.marklong.domain.stock.domain.Stock;
import com.example.marklong.domain.stock.repository.StockRepository;
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
    private final HoldingHistoryQueryRepository holdingHistoryQueryRepository;
    private final StockRepository stockRepository;

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
        BigDecimal avgPriceAtTrade = holding.getAvgPrice(); // 먼저 캡처

        holding.sell(request.getQuantity());

        holdingHistoryRepository.save(
                HoldingHistory.builder()
                        .userId(userId)
                        .stockCode(request.getStockCode())
                        .tradeType(TradeType.SELL)
                        .quantity(request.getQuantity())
                        .price(request.getPrice())
                        .avgPriceAtTrade(avgPriceAtTrade)   // 매도 시 원래 평단가는 안바뀌지만, 혹시 모르니 이렇게
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

    @Transactional(readOnly = true)
    public HoldingDetailResponse getHolding(Long userId, Long holdingId) {
        Holding holding = holdingRepository.findByIdAndUserIdAndDeletedAtIsNull(holdingId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HOLDING_NOT_FOUND));
        Stock stock = stockRepository.findByStockCodeAndActiveTrue(holding.getStockCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        // todo: redis 연동, timescaleDB에서 현재가 가져오기
        return new HoldingDetailResponse(
                holding.getId(),
                stock.getStockCode(),
                stock.getName(),
                holding.getAvgPrice(),
                BigDecimal.ZERO,    // 현재가
                holding.getTotalQuantity(),
                BigDecimal.ZERO,    // 미실현손익
                BigDecimal.ZERO     //  미실현손익 퍼센트
        );
    }

    @Transactional(readOnly = true)
    public List<HoldingHistoryResponse> searchHistory(Long userId, HistorySearchCondition condition) {
        return holdingHistoryQueryRepository.searchHistory(userId, condition);
    }

    private Holding getHoldingOrThrow(Long userId, String stockCode) {
        return holdingRepository.findByUserIdAndStockCodeAndDeletedAtIsNull(userId, stockCode)
                .orElseThrow(()->new BusinessException(ErrorCode.HOLDING_NOT_FOUND));
    }
}
