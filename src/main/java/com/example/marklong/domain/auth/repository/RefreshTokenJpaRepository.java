package com.example.marklong.domain.auth.repository;

import com.example.marklong.domain.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    Optional<RefreshToken> findByUserId(Long userId);
    void deleteByUserId(Long userId);
    void deleteAllByExpiresAtBefore(LocalDateTime expireTime);
}
