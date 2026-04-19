package com.example.marklong.domain.post.repository;

import com.example.marklong.domain.post.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post,Long> {
    List<Post> findAllByStockCodeAndDeletedAtIsNullOrderByCreatedAtDesc(String stockCode);
    Optional<Post> findByIdAndDeletedAtIsNull(Long id);
    Optional<Post> findByIdAndUserIdAndDeletedAtIsNull(Long id,Long userId);
}
