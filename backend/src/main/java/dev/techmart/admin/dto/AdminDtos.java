package dev.techmart.admin.dto;

import dev.techmart.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AdminDtos {

    private AdminDtos() {
    }

    @Schema(description = "Doanh thu theo mot moc thoi gian")
    public record RevenuePoint(
            @Schema(example = "2026-08-15") LocalDate period,
            BigDecimal revenue,
            long orderCount,
            BigDecimal averageOrderValue
    ) {}

    public record TopProduct(
            Long variantId,
            String productName,
            String variantName,
            String sku,
            long quantitySold,
            BigDecimal revenue
    ) {}

    @Schema(description = "So lieu tong quan cho man hinh dau tien cua admin")
    public record DashboardSummary(
            BigDecimal revenueToday,
            BigDecimal revenueThisMonth,
            long ordersToday,
            long ordersThisMonth,
            BigDecimal averageOrderValue,
            @Schema(description = "So don theo tung trang thai") Map<OrderStatus, Long> ordersByStatus,
            long lowStockVariants,
            long pendingOutboxEvents,
            long activeReservations
    ) {}

    public record LowStockItem(
            Long variantId,
            String sku,
            String productName,
            String variantName,
            int stockQuantity,
            int reservedQuantity,
            int availableQuantity
    ) {}

    public record AdminOrderRow(
            String code,
            String customerEmail,
            OrderStatus status,
            BigDecimal totalAmount,
            int itemCount,
            java.time.Instant placedAt
    ) {}

    public record RevenueReport(
            String groupBy,
            LocalDate from,
            LocalDate to,
            BigDecimal totalRevenue,
            long totalOrders,
            List<RevenuePoint> points
    ) {}
}
