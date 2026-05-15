package com.example.marklong.domain.user.repository;

import com.example.marklong.domain.user.domain.OAuthProvider;
import com.example.marklong.domain.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findUserByIdAndDeletedAtIsNull(Long id);
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    Optional<User> findByOauthIdAndProvider(String oauthId, OAuthProvider provider);
    boolean existsByEmailAndDeletedAtIsNull(String email);
    boolean existsByNicknameAndDeletedAtIsNull(String nickname);
}
