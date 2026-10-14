package dev.techmart.audit.dto;

import dev.techmart.audit.domain.AuditAction;
import dev.techmart.audit.domain.AuditLog;

import java.time.Instant;
import java.util.Map;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditLogResponse(
            Long id,
            Long actorId,
            String actorEmail,
            String actorRole,
            AuditAction action,
            String targetType,
            String targetId,
            Map<String, Object> detail,
            Instant createdAt
    ) {
        public static AuditLogResponse from(AuditLog a) {
            return new AuditLogResponse(a.getId(), a.getActorId(), a.getActorEmail(), a.getActorRole(),
                    a.getAction(), a.getTargetType(), a.getTargetId(), a.getDetail(), a.getCreatedAt());
        }
    }
}
