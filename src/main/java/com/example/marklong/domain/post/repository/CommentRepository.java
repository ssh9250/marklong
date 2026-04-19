package com.example.marklong.domain.post.repository;

import com.example.marklong.domain.post.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findAllByPostIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long id);

    Optional<Comment> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
