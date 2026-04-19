package com.example.marklong.domain.holding.repository;

import com.example.marklong.domain.holding.domain.HoldingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HoldingHistoryRepository extends JpaRepository<HoldingHistory, Long> {
    List<HoldingHistory> findAllByUserIdOrderByTradedAtDesc(Long userId);
    List<HoldingHistory> findAllByUserIdAndStockCodeOrderByTradedAtDesc(Long userId, String stockCode);
}
