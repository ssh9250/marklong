package com.example.marklong.domain.news.schedular;

import com.example.marklong.domain.news.repository.NewsContentRepository;
import com.example.marklong.domain.news.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsSchedular {
    private final NewsRepository newsRepository;
    private final NewsContentRepository newsContentRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredContent() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        int deleted = newsContentRepository.deleteByCreatedAtBefore(threshold);
        log.info("뉴스 본문 삭제 완료: {}건", deleted);
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void deleteExpiredNews() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(90);
        int deleted = newsRepository.deleteByCreatedAtBefore(threshold);
        log.info("뉴스 메타데이터 삭제 완료: {}건", deleted);
    }

}
