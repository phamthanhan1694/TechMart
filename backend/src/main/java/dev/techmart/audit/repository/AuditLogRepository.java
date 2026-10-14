package dev.techmart.audit.repository;

import dev.techmart.audit.domain.AuditAction;
import dev.techmart.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
             WHERE (:action IS NULL OR a.action = :action)
               AND (:targetType IS NULL OR a.targetType = :targetType)
               AND (:targetId IS NULL OR a.targetId = :targetId)
               AND (:actorId IS NULL OR a.actorId = :actorId)
               AND (CAST(:from AS timestamp) IS NULL OR a.createdAt >= :from)
               AND (CAST(:to   AS timestamp) IS NULL OR a.createdAt <  :to)
             ORDER BY a.createdAt DESC, a.id DESC
            """)
    Page<AuditLog> search(@Param("action") AuditAction action,
                          @Param("targetType") String targetType,
                          @Param("targetId") String targetId,
                          @Param("actorId") Long actorId,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);

    long countByAction(AuditAction action);
}
