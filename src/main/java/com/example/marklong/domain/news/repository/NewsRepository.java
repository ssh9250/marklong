package com.example.marklong.domain.news.repository;

import com.example.marklong.domain.news.domain.News;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsRepository extends JpaRepository<News, Long> {
}
