package com.example.marklong.domain.holding.repository;

import com.example.marklong.domain.holding.domain.Holding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    List<Holding> findAllByUserIdAndDeletedAtIsNull(Long userId);
    Optional<Holding> findByUserIdAndStockCodeAndDeletedAtIsNull(Long userId, String stockCode);
    Optional<Holding> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
