package dev.techmart.identity.repository;

import dev.techmart.identity.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Thu hồi toàn bộ token còn sống của một user.
     * Gọi khi phát hiện tái sử dụng token đã bị thu hồi, và khi logout toàn cục.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now
             WHERE t.user.id = :userId
               AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
