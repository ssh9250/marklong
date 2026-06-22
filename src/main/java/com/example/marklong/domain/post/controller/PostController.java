package com.example.marklong.domain.post.controller;

import com.example.marklong.domain.post.dto.*;
import com.example.marklong.domain.post.service.CommentService;
import com.example.marklong.domain.post.service.PostService;
import com.example.marklong.global.response.ApiResponse;
import com.example.marklong.security.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
@Tag(name = "Post", description = "게시글 및 댓글 API")
public class PostController {

    private final PostService postService;
    private final CommentService commentService;

    // ── 게시글 ─────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "게시글 작성")
    public ResponseEntity<ApiResponse<Long>> create(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody PostCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(postService.create(authUser.userId(), request)));
    }

    @GetMapping
    @Operation(summary = "게시글 목록 검색", description = "종목 코드·제목·내용·작성자·기간·정렬 조건으로 검색합니다. 비로그인 접근 가능합니다.")
    public ResponseEntity<ApiResponse<List<PostListResponse>>> searchPosts(
            @ModelAttribute PostSearchCondition condition
    ) {
        return ResponseEntity.ok(ApiResponse.ok(postService.searchPosts(condition)));
    }

    @GetMapping("/me")
    @Operation(summary = "내 게시글 목록 조회")
    public ResponseEntity<ApiResponse<List<PostListResponse>>> getMyPosts(
            @AuthenticationPrincipal AuthUser authUser,
            @ModelAttribute PostSearchCondition condition
    ) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getMyPosts(authUser.userId(), condition)));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "게시글 단건 조회", description = "게시글 내용과 댓글 목록을 함께 반환합니다. 조회 시 조회수가 증가합니다.")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPost(
            @PathVariable Long postId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getPost(postId)));
    }

    @PutMapping("/{postId}")
    @Operation(summary = "게시글 수정")
    public ResponseEntity<ApiResponse<Void>> update(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long postId,
            @RequestBody PostUpdateRequest request
    ) {
        postService.update(authUser.userId(), postId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "게시글 삭제")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long postId
    ) {
        postService.delete(authUser.userId(), postId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── 댓글 ───────────────────────────────────────────────

    @PostMapping("/{postId}/comments")
    @Operation(summary = "댓글 작성")
    public ResponseEntity<ApiResponse<Long>> createComment(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long postId,
            @RequestBody CommentCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.create(authUser.userId(), postId, request)));
    }

    @GetMapping("/{postId}/comments")
    @Operation(summary = "게시글 댓글 목록 조회") // 어차피 post 단건 조회 시 같이 보내지만, 그래도 단일 api도 하나 만들어두자
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getComments(
            @PathVariable Long postId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.getCommentsByPostId(postId)));
    }

    @GetMapping("/me/comments")
    @Operation(summary = "내 댓글 목록 조회")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getMyComments(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.getMyComments(authUser.userId())));
    }

    @PutMapping("/comments/{commentId}")
    @Operation(summary = "댓글 수정")
    public ResponseEntity<ApiResponse<Void>> updateComment(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long commentId,
            @RequestBody CommentUpdateRequest request
    ) {
        commentService.update(authUser.userId(), commentId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "댓글 삭제")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long commentId
    ) {
        commentService.delete(authUser.userId(), commentId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
