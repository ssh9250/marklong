package com.example.marklong.domain.portfolio.service;

import com.example.marklong.domain.holding.domain.Holding;
import com.example.marklong.domain.holding.repository.HoldingRepository;
import com.example.marklong.domain.portfolio.domain.Portfolio;
import com.example.marklong.domain.portfolio.domain.PortfolioItem;
import com.example.marklong.domain.portfolio.dto.*;
import com.example.marklong.domain.portfolio.repository.PortfolioItemRepository;
import com.example.marklong.domain.portfolio.repository.PortfolioRepository;
import com.example.marklong.domain.stock.domain.Stock;
import com.example.marklong.domain.stock.repository.StockRepository;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;
    private final PortfolioItemRepository portfolioItemRepository;
    private final HoldingRepository holdingRepository;
    private final StockRepository stockRepository;

    public PortfolioResponse create(Long userId, PortfolioCreateRequest request) {
        Portfolio portfolio = Portfolio.builder()
                .userId(userId)
                .name(request.getName())
                .description(request.getDescription())
                .currency(request.getCurrency())
                .build();
        return PortfolioResponse.of(portfolioRepository.save(portfolio), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> getMyPortfolios(Long userId) {
        return portfolioRepository.findAllByUserIdAndDeletedAtIsNull(userId)
                .stream()
                .map(this::buildPortfolioResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PortfolioDetailResponse getOne(Long userId, Long portfolioId) {
        Portfolio portfolio = getPortfolioOrThrow(userId, portfolioId);
        return PortfolioDetailResponse.of(buildPortfolioResponse(portfolio), buildItemResponse(portfolioId));
    }

    public PortfolioResponse update(Long userId, Long portfolioId, PortfolioUpdateRequest request) {
        Portfolio portfolio = getPortfolioOrThrow(userId, portfolioId);
        portfolio.update(request.getName(), request.getDescription());
        return buildPortfolioResponse(portfolio);
    }

    public void delete(Long userId, Long portfolioId) {
        Portfolio portfolio = getPortfolioOrThrow(userId, portfolioId);
        portfolioItemRepository.findAllByPortfolioIdAndDeletedAtIsNull(portfolioId)
                .forEach(pi -> {
                    Holding holding = holdingRepository.findById(pi.getHoldingId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.HOLDING_NOT_FOUND));
                    holding.deallocate(pi.getAllocatedQuantity());
                    pi.delete();
                });
        portfolio.delete();
    }

    public PortfolioItemResponse allocate(Long userId, Long portfolioId, AllocateRequest request) {
        getPortfolioOrThrow(userId, portfolioId);
        Stock stock = stockRepository.findByStockCodeAndActiveTrue(request.getStockCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
        Holding holding = holdingRepository.findByUserIdAndStockCodeAndDeletedAtIsNull(userId, request.getStockCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.HOLDING_NOT_FOUND));

        holding.allocate(request.getQuantity());

        PortfolioItem item = portfolioItemRepository.findByPortfolioIdAndHoldingIdAndDeletedAtIsNull(portfolioId, holding.getId())
                .map(existing -> {
                    existing.updateAllocation(existing.getAllocatedQuantity().add(request.getQuantity()));
                    return existing;
                })
                .orElseGet(() -> portfolioItemRepository.save(
                        PortfolioItem.builder()
                                .portfolioId(portfolioId)
                                .holdingId(holding.getId())
                                .stockCode(request.getStockCode())
                                .allocatedQuantity(request.getQuantity())
                                .build()
                ));
        return PortfolioItemResponse.of(item, stock.getName(), holding, BigDecimal.ZERO);   //  todo: currentPrice 변경 필요
    }

    public void deallocate(Long userId, Long portfolioId, Long itemId) {
        getPortfolioOrThrow(userId, portfolioId);
        PortfolioItem item = portfolioItemRepository.findByIdAndDeletedAtIsNull(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_ITEM_NOT_FOUND));
        holdingRepository.findByUserIdAndStockCodeAndDeletedAtIsNull(userId, item.getStockCode())
                .ifPresent(holding -> {holding.deallocate(item.getAllocatedQuantity());});

        item.delete();
    }


    private Portfolio getPortfolioOrThrow(Long userId, Long portfolioId) {
        return portfolioRepository.findByIdAndUserIdAndDeletedAtIsNull(portfolioId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));
    }

    private PortfolioResponse buildPortfolioResponse(Portfolio portfolio) {
        List<PortfolioItemResponse> items = buildItemResponse(portfolio.getId());
        BigDecimal totalInvestment = items.stream()
                .map(PortfolioItemResponse::getInvestment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalValuation = items.stream()
                .map(PortfolioItemResponse::getValuation)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return PortfolioResponse.of(portfolio, totalInvestment, totalValuation);
    }

    private List<PortfolioItemResponse> buildItemResponse(Long portfolioId) {
        return portfolioItemRepository.findAllByPortfolioIdAndDeletedAtIsNull(portfolioId)
                .stream()
                .map(item -> {
                    Holding holding = holdingRepository.findById(item.getId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.HOLDING_NOT_FOUND));
                    Stock stock = stockRepository.findByStockCodeAndActiveTrue(item.getStockCode())
                            .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

                    return PortfolioItemResponse.of(item, stock.getName(), holding, BigDecimal.ZERO); // 현재가는 나중에 외부api, redis로 가져옴
                })
                .toList();
    }
}
