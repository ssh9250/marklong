package com.example.marklong.domain.news.controller;

import com.example.marklong.domain.news.dto.NewsContentResponse;
import com.example.marklong.domain.news.dto.NewsDetailResponse;
import com.example.marklong.domain.news.dto.NewsListResponse;
import com.example.marklong.domain.news.dto.NewsSearchCondition;
import com.example.marklong.domain.news.service.NewsService;
import com.example.marklong.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/news")
@Tag(name = "News", description = "뉴스 API — 뉴스 저장은 Kafka consumer를 통해 자동으로 이루어집니다. 삭제는 스케줄링을 통해 전문은 30일, 본문은 90일 단위로 처리됩니다.")
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    @Operation(summary = "뉴스 목록 조회")
    public ResponseEntity<ApiResponse<List<NewsListResponse>>> getNewsList() {
        return ResponseEntity.ok(ApiResponse.ok(newsService.getNewsList()));
    }

    @GetMapping("/search")
    @Operation(summary = "뉴스 검색", description = "제공사·작성자·제목·종목코드·카테고리·감성·중요도·기간으로 검색합니다. category, sentiment, importance는 반복 파라미터로 복수 전달 가능합니다.")
    public ResponseEntity<ApiResponse<List<NewsListResponse>>> searchNews(
            @ModelAttribute NewsSearchCondition condition
    ) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.searchNewsList(condition)));
    }

    @GetMapping("/{newsId}")
    @Operation(summary = "뉴스 상세 조회", description = "메타데이터를 반환합니다. 본문이 필요하면 /content를 사용하세요.")
    public ResponseEntity<ApiResponse<NewsDetailResponse>> getNewsDetail(
            @PathVariable Long newsId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.getNewsDetail(newsId)));
    }

    @GetMapping("/{newsId}/content")
    @Operation(summary = "뉴스 본문 조회", description = "본문은 별도 테이블에 저장되며 30일 후 삭제됩니다.")
    public ResponseEntity<ApiResponse<NewsContentResponse>> getNewsContent(
            @PathVariable Long newsId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.getNewsContent(newsId)));
    }
}
