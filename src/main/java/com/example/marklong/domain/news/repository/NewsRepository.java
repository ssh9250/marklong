package com.example.marklong.domain.news.repository;

import com.example.marklong.domain.news.domain.News;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface NewsRepository extends JpaRepository<News, Long> {
    boolean existsBySourceId(String sourceId);
    int deleteByCreatedAtBefore(LocalDateTime createdAt);
}
