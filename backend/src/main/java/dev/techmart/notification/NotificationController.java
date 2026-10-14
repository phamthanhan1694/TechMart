package dev.techmart.notification;

import dev.techmart.common.web.PageResponse;
import dev.techmart.common.web.PageableSanitizer;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.notification.NotificationService.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification", description = "Thong bao trong ung dung, sinh tu outbox. Client polling unread-count.")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Thong bao cua toi", description = "Moi nhat dung dau.")
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        int safeSize = Math.min(Math.max(size, 1), PageableSanitizer.MAX_PAGE_SIZE);
        return service.list(principal.id(), Math.max(page, 0), safeSize);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "So thong bao chua doc",
            description = "Endpoint duoc polling dinh ky - chay tren partial index WHERE read_at IS NULL.")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("count", service.unreadCount(principal.id()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Danh dau da doc")
    public void markRead(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        service.markRead(principal.id(), id);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Danh dau tat ca da doc")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("updated", service.markAllRead(principal.id()));
    }
}
