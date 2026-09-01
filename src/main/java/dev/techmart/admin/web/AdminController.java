package dev.techmart.admin.web;

import dev.techmart.admin.dto.AdminDtos.*;
import dev.techmart.admin.service.AdminService;
import dev.techmart.common.web.PageResponse;
import dev.techmart.common.web.PageableSanitizer;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.order.domain.OrderStatus;
import dev.techmart.order.dto.OrderDtos.OrderResponse;
import dev.techmart.order.dto.OrderDtos.UpdateStatusRequest;
import dev.techmart.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Quan ly don hang, ton kho va bao cao doanh thu")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminController {

    private final AdminService adminService;
    private final OrderService orderService;

    public AdminController(AdminService adminService, OrderService orderService) {
        this.adminService = adminService;
        this.orderService = orderService;
    }

    // ------------------------------------------------------------------ don hang

    @GetMapping("/orders")
    @Operation(summary = "Danh sach don hang", description = "Loc theo trang thai va khoang ngay.")
    public PageResponse<AdminOrderRow> orders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return adminService.listOrders(status, from, to, PageableSanitizer.sanitize(pageable));
    }

    @PatchMapping("/orders/{code}/status")
    @Operation(summary = "Doi trang thai don hang",
            description = """
                    Di qua bang chuyen trang thai o `OrderStateMachine`. Chuyen doi khong hop le
                    tra `409 INVALID_STATE_TRANSITION`; khong du quyen tra `403`.

                    Cac chuyen doi lam hang quay ve kho (huy, hoan tra) se tu dong hoan ton kho
                    va hoan luot ma giam gia.
                    """)
    public OrderResponse updateStatus(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable String code,
                                      @Valid @RequestBody UpdateStatusRequest req) {
        return orderService.updateStatus(code, principal, req);
    }

    // ------------------------------------------------------------------ ton kho

    @GetMapping("/inventory/low-stock")
    @Operation(summary = "Variant sap het hang")
    public List<LowStockItem> lowStock(@RequestParam(defaultValue = "10") int threshold) {
        return adminService.lowStock(threshold);
    }

    // ------------------------------------------------------------------ bao cao

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "So lieu tong quan",
            description = "Doanh thu hom nay/thang nay, so don theo trang thai, "
                    + "so variant sap het hang, so event outbox chua xu ly, so phien dang giu hang.")
    public DashboardSummary summary() {
        return adminService.summary();
    }

    @GetMapping("/dashboard/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Doanh thu theo ngay hoac thang",
            description = """
                    Tinh bang query aggregate o DB (`date_trunc` + `GROUP BY`), khong nap don hang
                    ve ung dung roi cong bang Java - cach do chay duoc voi 400 don va chet voi 400.000.

                    Chi tinh cac trang thai da thu duoc tien: PAID, PACKED, SHIPPED, DELIVERED.
                    """)
    public RevenueReport revenue(
            @RequestParam(defaultValue = "day") String groupBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return adminService.revenue(groupBy, from, to);
    }

    @GetMapping("/dashboard/top-products")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "San pham ban chay nhat")
    public List<TopProduct> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return adminService.topProducts(from, to, limit);
    }
}
