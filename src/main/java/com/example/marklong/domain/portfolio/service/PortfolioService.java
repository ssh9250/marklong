package com.example.marklong.domain.portfolio.service;

import com.example.marklong.domain.portfolio.domain.Portfolio;
import com.example.marklong.domain.portfolio.domain.PortfolioItem;
import com.example.marklong.domain.portfolio.dto.PortfolioCreateRequest;
import com.example.marklong.domain.portfolio.dto.PortfolioItemResponse;
import com.example.marklong.domain.portfolio.dto.PortfolioResponse;
import com.example.marklong.domain.portfolio.dto.PortfolioUpdateRequest;
import com.example.marklong.domain.portfolio.repository.PortfolioItemRepository;
import com.example.marklong.domain.portfolio.repository.PortfolioRepository;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;
    private final PortfolioItemRepository portfolioItemRepository;

    public PortfolioResponse create(Long userId, PortfolioCreateRequest request) {
        Portfolio portfolio = Portfolio.builder()
                .userId(userId)
                .name(request.getName())
                .description(request.getDescription())
                .currency(request.getCurrency())
                .build();
        return PortfolioResponse.from(portfolioRepository.save(portfolio));
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> getMyPortfolios(Long userId) {
        return portfolioRepository.findAllByUserIdAndDeletedAtIsNull(userId)
                .stream()
                .map(PortfolioResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getOne(Long userId, Long portfolioId) {
        Portfolio portfolio = getPortfolioOrThrow(userId, portfolioId);
        return PortfolioResponse.from(portfolio);
    }

    public PortfolioResponse update(Long userId, Long portfolioId, PortfolioUpdateRequest request) {
        Portfolio portfolio = getPortfolioOrThrow(userId, portfolioId);
        portfolio.update(request.getName(), request.getDescription());
        return PortfolioResponse.from(portfolio);
    }

    public void delete(Long userId, Long portfolioId) {
        Portfolio portfolio = getPortfolioOrThrow(userId, portfolioId);
        portfolio.delete();
    }

//    public PortfolioItemResponse addItem(Long userId, Long portfolioId, TradeRequest request) {
//        getPortfolioOrThrow(userId, portfolioId);
//
//        PortfolioItem item = portfolioItemRepository
//                .findByPortfolioIdAndStockCodeAndDeletedAtIsNull(portfolioId, request.getStockCode());
//    }

    private Portfolio getPortfolioOrThrow(Long userId, Long portfolioId) {
        return portfolioRepository.findByIdAndUserIdAndDeletedAtIsNull(portfolioId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));
    }
}
