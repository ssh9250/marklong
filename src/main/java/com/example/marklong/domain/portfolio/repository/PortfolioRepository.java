package com.example.marklong.domain.portfolio.repository;

import com.example.marklong.domain.portfolio.domain.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findAllByUserIdAndDeletedAtIsNull(Long userId);
    Optional<Portfolio> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
