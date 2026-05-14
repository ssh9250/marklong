package com.example.marklong.domain.news.repository;

import com.example.marklong.domain.news.domain.NewsContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NewsContentRepository extends JpaRepository<NewsContent,Long> {
    Optional<NewsContent> findByNewsId(Long newsId);
    int deleteByCreatedAtBefore(LocalDateTime createdAt);

}
