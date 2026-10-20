package dev.techmart.warranty.web;

import dev.techmart.common.web.PageResponse;
import dev.techmart.common.web.PageableSanitizer;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.warranty.domain.WarrantyStatus;
import dev.techmart.warranty.dto.WarrantyDtos.*;
import dev.techmart.warranty.service.SerialService;
import dev.techmart.warranty.service.WarrantyService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Warranty", description = "Tồn kho theo serial/IMEI và bảo hành")
@RequiredArgsConstructor
public class WarrantyController {

    private final SerialService serialService;
    private final WarrantyService warrantyService;

    // ------------------------------------------------------------------ serial

    @GetMapping("/serials/{serialNo}/warranty")
    @SecurityRequirements
    @Operation(summary = "Tra cứu bảo hành theo serial/IMEI",
            description = """
                    Công khai, không cần đăng nhập - giống mọi trang tra bảo hành thật.

                    Thời hạn bảo hành tính từ lúc **xuất kho** (khi đơn chuyển sang PACKED),
                    không phải từ lúc nhập kho.
                    """)
    public WarrantyLookupResponse lookup(@PathVariable String serialNo) {
        return serialService.lookup(serialNo);
    }

    @PostMapping("/admin/serials/bulk")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "Nhập lô serial khi nhận hàng từ nhà cung cấp",
            description = "Serial đã tồn tại sẽ bị bỏ qua và liệt kê lại, không làm hỏng cả lô.")
    public BulkSerialResponse importBulk(@Valid @RequestBody BulkSerialRequest req) {
        return serialService.importBulk(req);
    }

    @GetMapping("/admin/order-items/{orderItemId}/serials")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "Các serial đã gán cho một dòng đơn hàng")
    public List<SerialResponse> serialsOfOrderItem(@PathVariable Long orderItemId) {
        return serialService.findByOrderItem(orderItemId);
    }

    // ------------------------------------------------------------------ bảo hành

    @PostMapping("/warranty-requests")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Tạo yêu cầu bảo hành",
            description = """
                    Kiểm quyền sở hữu qua chuỗi `serial -> order_item -> order -> user`:
                    chỉ người đã mua đúng đơn vị hàng đó mới yêu cầu được.
                    Serial của người khác trả `404`, không phải `403`.

                    Một serial chỉ có một yêu cầu đang mở tại một thời điểm.
                    """)
    public ResponseEntity<WarrantyRequestResponse> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateWarrantyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(warrantyService.create(principal, req));
    }

    @GetMapping("/warranty-requests")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách yêu cầu bảo hành",
            description = "Khách chỉ thấy yêu cầu của mình; STAFF/ADMIN thấy tất cả.")
    public PageResponse<WarrantyRequestResponse> search(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) WarrantyStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return warrantyService.search(principal, status, PageableSanitizer.sanitize(pageable));
    }

    @GetMapping("/warranty-requests/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiết một yêu cầu bảo hành")
    public WarrantyRequestResponse findById(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable Long id) {
        return warrantyService.findById(principal, id);
    }

    @PatchMapping("/warranty-requests/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "Duyệt, từ chối hoặc hoàn tất yêu cầu bảo hành",
            description = """
                    REQUESTED → APPROVED / REJECTED, APPROVED → COMPLETED.
                    Chuyển đổi khác trả `409 INVALID_STATE_TRANSITION`.

                    Duyệt bảo hành đồng nghĩa máy có lỗi thật, nên serial được đánh dấu
                    DEFECTIVE để không quay lại kho bán tiếp.
                    """)
    public WarrantyRequestResponse resolve(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable Long id,
                                           @Valid @RequestBody ResolveWarrantyRequest req) {
        return warrantyService.resolve(principal, id, req);
    }
}
