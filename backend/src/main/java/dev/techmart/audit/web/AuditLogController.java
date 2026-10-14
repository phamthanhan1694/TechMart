package dev.techmart.audit.web;

import dev.techmart.audit.domain.AuditAction;
import dev.techmart.audit.dto.AuditDtos.AuditLogResponse;
import dev.techmart.audit.repository.AuditLogRepository;
import dev.techmart.common.config.AppProperties;
import dev.techmart.common.web.PageResponse;
import dev.techmart.common.web.PageableSanitizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "Admin - Audit log", description = "Nhat ky hanh dong quan tri, chi them khong sua")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogRepository repository;
    private final ZoneId zone;

    public AuditLogController(AuditLogRepository repository, AppProperties props) {
        this.repository = repository;
        this.zone = props.reporting().zone();
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Tra cuu nhat ky",
            description = "Loc theo hanh dong, doi tuong, nguoi thuc hien va khoang ngay (gio Viet Nam).")
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 30) Pageable pageable) {
        Pageable page = PageableSanitizer.sanitize(pageable);
        Instant fromInstant = from != null ? from.atStartOfDay(zone).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(zone).toInstant() : null;
        // Thứ tự sắp xếp cố định trong query - bỏ sort từ client để không ghép thứ tự lạ vào JPQL
        return PageResponse.from(repository.search(action, blankToNull(targetType), blankToNull(targetId),
                        actorId, fromInstant, toInstant, PageRequest.of(page.getPageNumber(), page.getPageSize())),
                AuditLogResponse::from);
    }

    @GetMapping("/actions")
    @Operation(summary = "Danh sach hanh dong co the loc")
    public AuditAction[] actions() {
        return AuditAction.values();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
