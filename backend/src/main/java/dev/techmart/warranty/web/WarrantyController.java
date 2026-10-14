package dev.techmart.warranty.web;

import dev.techmart.common.web.PageResponse;
import dev.techmart.common.web.PageableSanitizer;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.warranty.domain.WarrantyStatus;
import dev.techmart.warranty.dto.WarrantyDtos.*;
import dev.techmart.warranty.service.SerialService;
import dev.techmart.warranty.service.WarrantyService;
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
@Tag(name = "Warranty", description = "Ton kho theo serial/IMEI va bao hanh")
public class WarrantyController {

    private final SerialService serialService;
    private final WarrantyService warrantyService;

    public WarrantyController(SerialService serialService, WarrantyService warrantyService) {
        this.serialService = serialService;
        this.warrantyService = warrantyService;
    }

    // ------------------------------------------------------------------ serial

    @GetMapping("/serials/{serialNo}/warranty")
    @SecurityRequirements
    @Operation(summary = "Tra cuu bao hanh theo serial/IMEI",
            description = """
                    Cong khai, khong can dang nhap - giong moi trang tra bao hanh that.

                    Thoi han bao hanh tinh tu luc **xuat kho** (khi don chuyen sang PACKED),
                    khong phai tu luc nhap kho.
                    """)
    public WarrantyLookupResponse lookup(@PathVariable String serialNo) {
        return serialService.lookup(serialNo);
    }

    @PostMapping("/admin/serials/bulk")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "Nhap lo serial khi nhan hang tu nha cung cap",
            description = "Serial da ton tai se bi bo qua va liet ke lai, khong lam hong ca lo.")
    public BulkSerialResponse importBulk(@Valid @RequestBody BulkSerialRequest req) {
        return serialService.importBulk(req);
    }

    @GetMapping("/admin/order-items/{orderItemId}/serials")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "Cac serial da gan cho mot dong don hang")
    public List<SerialResponse> serialsOfOrderItem(@PathVariable Long orderItemId) {
        return serialService.findByOrderItem(orderItemId);
    }

    // ------------------------------------------------------------------ bảo hành

    @PostMapping("/warranty-requests")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Tao yeu cau bao hanh",
            description = """
                    Kiem quyen so huu qua chuoi `serial -> order_item -> order -> user`:
                    chi nguoi da mua dung don vi hang do moi yeu cau duoc.
                    Serial cua nguoi khac tra `404`, khong phai `403`.

                    Mot serial chi co mot yeu cau dang mo tai mot thoi diem.
                    """)
    public ResponseEntity<WarrantyRequestResponse> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateWarrantyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(warrantyService.create(principal, req));
    }

    @GetMapping("/warranty-requests")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sach yeu cau bao hanh",
            description = "Khach chi thay yeu cau cua minh; STAFF/ADMIN thay tat ca.")
    public PageResponse<WarrantyRequestResponse> search(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) WarrantyStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return warrantyService.search(principal, status, PageableSanitizer.sanitize(pageable));
    }

    @GetMapping("/warranty-requests/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiet mot yeu cau bao hanh")
    public WarrantyRequestResponse findById(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable Long id) {
        return warrantyService.findById(principal, id);
    }

    @PatchMapping("/warranty-requests/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "Duyet, tu choi hoac hoan tat yeu cau bao hanh",
            description = """
                    REQUESTED → APPROVED / REJECTED, APPROVED → COMPLETED.
                    Chuyen doi khac tra `409 INVALID_STATE_TRANSITION`.

                    Duyet bao hanh dong nghia may co loi that, nen serial duoc danh dau
                    DEFECTIVE de khong quay lai kho ban tiep.
                    """)
    public WarrantyRequestResponse resolve(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable Long id,
                                           @Valid @RequestBody ResolveWarrantyRequest req) {
        return warrantyService.resolve(principal, id, req);
    }
}
