package com.example.marklong.domain.portfolio.repository;

import com.example.marklong.domain.portfolio.domain.PortfolioItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, Long> {
    List<PortfolioItem> findAllByPortfolioIdAndDeletedAtIsNull(Long portfolioId);
    Optional<PortfolioItem> findByPortfolioIdAndHoldingIdAndDeletedAtIsNull(Long portfolioId, Long holdingId);
    Optional<PortfolioItem> findByIdAndDeletedAtIsNull(Long id);
    List<PortfolioItem> findAllByHoldingIdAndDeletedAtIsNull(Long holdingId);
}