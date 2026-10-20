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
import lombok.RequiredArgsConstructor;
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
@Tag(name = "Admin", description = "Quản lý đơn hàng, tồn kho và báo cáo doanh thu")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final OrderService orderService;

    // ------------------------------------------------------------------ đơn hàng

    @GetMapping("/orders")
    @Operation(summary = "Danh sách đơn hàng", description = "Lọc theo trạng thái và khoảng ngày.")
    public PageResponse<AdminOrderRow> orders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return adminService.listOrders(status, from, to, PageableSanitizer.sanitize(pageable));
    }

    @PatchMapping("/orders/{code}/status")
    @Operation(summary = "Đổi trạng thái đơn hàng",
            description = """
                    Đi qua bảng chuyển trạng thái ở `OrderStateMachine`. Chuyển đổi không hợp lệ
                    trả `409 INVALID_STATE_TRANSITION`; không đủ quyền trả `403`.

                    Các chuyển đổi làm hàng quay về kho (huỷ, hoàn trả) sẽ tự động hoàn tồn kho
                    và hoàn lượt mã giảm giá.
                    """)
    public OrderResponse updateStatus(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable String code,
                                      @Valid @RequestBody UpdateStatusRequest req) {
        return orderService.updateStatus(code, principal, req);
    }

    // ------------------------------------------------------------------ tồn kho

    @GetMapping("/inventory/low-stock")
    @Operation(summary = "Variant sắp hết hàng")
    public List<LowStockItem> lowStock(@RequestParam(defaultValue = "10") int threshold) {
        return adminService.lowStock(threshold);
    }

    // ------------------------------------------------------------------ báo cáo

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Số liệu tổng quan",
            description = "Doanh thu hôm nay/tháng này, số đơn theo trạng thái, "
                    + "số variant sắp hết hàng, số event outbox chưa xử lý, số phiên đang giữ hàng.")
    public DashboardSummary summary() {
        return adminService.summary();
    }

    @GetMapping("/dashboard/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Doanh thu theo ngày hoặc tháng",
            description = """
                    Tính bằng query aggregate ở DB (`date_trunc` + `GROUP BY`), không nạp đơn hàng
                    về ứng dụng rồi cộng bằng Java - cách đó chạy được với 400 đơn và chết với 400.000.

                    Chỉ tính các trạng thái đã thu được tiền: PAID, PACKED, SHIPPED, DELIVERED.
                    """)
    public RevenueReport revenue(
            @RequestParam(defaultValue = "day") String groupBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return adminService.revenue(groupBy, from, to);
    }

    @GetMapping("/dashboard/top-products")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Sản phẩm bán chạy nhất")
    public List<TopProduct> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return adminService.topProducts(from, to, limit);
    }
}
