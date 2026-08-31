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
     * Thu hoi toan bo token con song cua mot user.
     * Goi khi phat hien tai su dung token da bi thu hoi, va khi logout toan cuc.
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
