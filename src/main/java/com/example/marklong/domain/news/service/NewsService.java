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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class NewsService {
    private final NewsRepository newsRepository;
    private final NewsQueryRepository newsQueryRepository;
    private final NewsContentRepository newsContentRepository;

    // delete -> 스케쥴러 (news:90, news content : 30) => 메타 데이터만 더 오래 볼 수 있게
    // update => x
    // create => kafka consumer
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
