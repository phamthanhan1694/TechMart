package dev.techmart.notification;

import dev.techmart.common.web.PageResponse;
import dev.techmart.common.web.PageableSanitizer;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.notification.NotificationService.NotificationResponse;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification", description = "Thông báo trong ứng dụng, sinh từ outbox. Client polling unread-count.")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    @Operation(summary = "Thông báo của tôi", description = "Mới nhất đứng đầu.")
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        int safeSize = Math.min(Math.max(size, 1), PageableSanitizer.MAX_PAGE_SIZE);
        return service.list(principal.id(), Math.max(page, 0), safeSize);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Số thông báo chưa đọc",
            description = "Endpoint được polling định kỳ - chạy trên partial index WHERE read_at IS NULL.")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("count", service.unreadCount(principal.id()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đánh dấu đã đọc")
    public void markRead(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        service.markRead(principal.id(), id);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Đánh dấu tất cả đã đọc")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("updated", service.markAllRead(principal.id()));
    }
}
