package com.example.marklong.domain.news.service;

import com.example.marklong.domain.news.domain.News;
import com.example.marklong.domain.news.domain.NewsContent;
import com.example.marklong.domain.news.dto.NewsContentResponse;
import com.example.marklong.domain.news.dto.NewsDetailResponse;
import com.example.marklong.domain.news.dto.NewsListResponse;
import com.example.marklong.domain.news.repository.NewsContentRepository;
import com.example.marklong.domain.news.repository.NewsQueryRepository;
import com.example.marklong.domain.news.repository.NewsRepository;
import com.example.marklong.global.exception.BusinessException;
import com.example.marklong.global.exception.ErrorCode;
import com.example.marklong.infra.kafka.dto.NewsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NewsService {
    private final NewsRepository newsRepository;
    private final NewsQueryRepository newsQueryRepository;
    private final NewsContentRepository newsContentRepository;

    // delete -> 스케쥴러 (news:90, news content : 30) => 메타 데이터만 더 오래 볼 수 있게
    // update => x
    // create => kafka consumer

    public void saveFromKafka(NewsEvent newsEvent) {
        if (newsRepository.existsBySourceId(newsEvent.getSourceId())) {
            log.debug("중복 뉴스 스킵: {}", newsEvent.getSourceId());
            return;
        }

        News news = News.builder()
                .sourceId(newsEvent.getSourceId())
                .provider(newsEvent.getProvider())
                .author(newsEvent.getAuthor())
                .title(newsEvent.getTitle())
                .summary(newsEvent.getSummary())
                .originalUrl(newsEvent.getAuthor())
                .stockCode(newsEvent.getStockCode())
                .category(newsEvent.getCategory())
                .sentiment(newsEvent.getSentiment())
                .importance(newsEvent.getImportance())
                .publishedAt(newsEvent.getPublishedAt())
                .build();

        News saved = newsRepository.save(news);

        if (newsEvent.getContent() != null) {
            NewsContent content = NewsContent.builder()
                    .newsId(saved.getId())
                    .content(newsEvent.getContent())
                    .build();
            newsContentRepository.save(content);
        }

    }

    @Transactional(readOnly = true)
    public List<NewsListResponse> getNewsList() {
        return newsQueryRepository.get();
    }

    @Transactional(readOnly = true)
    public NewsDetailResponse getNewsDetail(Long newsId) {
        News news = getNewsOrThrow(newsId);
        return NewsDetailResponse.from(news);
    }

    @Transactional(readOnly = true)
    public NewsContentResponse getNewsContent(Long newsId) {
        return NewsContentResponse.from(newsContentRepository.findByNewsId(newsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND)));
    }

    private News getNewsOrThrow(Long newsId) {
        return newsRepository.findById(newsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DATA_NOT_FOUND));
    }
}
