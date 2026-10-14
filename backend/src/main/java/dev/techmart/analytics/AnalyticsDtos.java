package dev.techmart.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTO của trang thống kê. Mọi con số đều do DB tính sẵn (GROUP BY, FILTER, percentile) -
 * ứng dụng chỉ ghép lại, không cộng dồn hàng nghìn dòng bằng Java.
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    @Schema(description = "Khoang ngay, tinh ca hai dau, theo mui gio bao cao")
    public record Range(LocalDate from, LocalDate to, int days) {}

    @Schema(description = "Mot chi so kem ky truoc cung do dai de so sanh")
    public record Kpi(
            BigDecimal current,
            BigDecimal previous,
            @Schema(description = "% thay doi so voi ky truoc; null khi ky truoc bang 0 (khong chia duoc)")
            BigDecimal changePercent
    ) {}

    public record Overview(
            Range range,
            Range previousRange,
            Kpi revenue,
            Kpi paidOrders,
            Kpi averageOrderValue,
            Kpi unitsSold,
            Kpi placedOrders,
            @Schema(description = "Ti le huy tren tong don dat, don vi %") Kpi cancelRate,
            Kpi discountTotal,
            Kpi newCustomers,
            Kpi buyers,
            @Schema(description = "% nguoi mua trong ky da tung mua truoc ky, don vi %") Kpi returningBuyerRate
    ) {}

    public record SeriesPoint(
            LocalDate period,
            BigDecimal revenue,
            long paidOrders,
            long placedOrders,
            long cancelledOrders,
            long newCustomers
    ) {}

    public record TimeSeries(
            String groupBy,
            Range range,
            List<SeriesPoint> points,
            @Schema(description = "Ky truoc cung do dai, can theo chi so - de ve duong so sanh")
            List<SeriesPoint> previous
    ) {}

    public record BreakdownRow(
            String key,
            String label,
            BigDecimal revenue,
            long orders,
            long units,
            @Schema(description = "Ti trong doanh so, don vi %") BigDecimal share
    ) {}

    public record Breakdown(
            String dimension,
            @Schema(description = "category/brand: doanh so hang hoa (tong line_total, truoc giam gia va phi ship). "
                    + "province: doanh thu don (total_amount).")
            String measure,
            BigDecimal total,
            List<BreakdownRow> rows
    ) {}

    public record FunnelStage(String stage, long count, BigDecimal percentOfPlaced) {}

    public record Funnel(
            Range range,
            List<FunnelStage> stages,
            long cancelled,
            long refunded,
            long pendingPayment,
            @Schema(description = "Trung vi so gio tu dat den thanh toan") BigDecimal medianHoursToPay,
            @Schema(description = "Trung vi so gio tu dat den giao thanh cong") BigDecimal medianHoursToDeliver
    ) {}

    public record HeatCell(
            @Schema(description = "1 = Thu Hai ... 7 = Chu Nhat (ISO)") int dayOfWeek,
            int hour,
            long orders,
            BigDecimal revenue
    ) {}

    public record Heatmap(Range range, String zone, List<HeatCell> cells) {}

    public record TopCustomer(Long userId, String email, String fullName, long orders, BigDecimal revenue) {}

    public record CustomerStats(
            Range range,
            long newCustomers,
            long buyers,
            long returningBuyers,
            BigDecimal returningBuyerRate,
            List<TopCustomer> topCustomers
    ) {}

    public record PaymentMethodStats(
            String method,
            long attempts,
            long succeeded,
            long failed,
            BigDecimal successRate,
            BigDecimal amount
    ) {}

    public record CouponRow(String code, long orders, BigDecimal discount, BigDecimal revenue) {}

    public record CouponStats(
            Range range,
            long paidOrders,
            long ordersWithCoupon,
            BigDecimal couponOrderShare,
            BigDecimal discountTotal,
            List<CouponRow> rows
    ) {}

    public record StockByCategory(String label, long units, BigDecimal value) {}

    public record SlowMover(
            Long variantId,
            String sku,
            String productName,
            String variantName,
            int availableQuantity,
            BigDecimal stockValue,
            Instant lastSoldAt
    ) {}

    public record InventoryStats(
            long outOfStock,
            long lowStock,
            long healthy,
            long totalUnits,
            long reservedUnits,
            @Schema(description = "Ton kho x gia ban hien tai - khong co gia von nen day KHONG phai gia tri so sach")
            BigDecimal stockValue,
            int lowThreshold,
            int slowDays,
            List<StockByCategory> byCategory,
            List<SlowMover> slowMovers
    ) {}

    public record LowRatedProduct(Long productId, String name, String slug, BigDecimal average, long reviews) {}

    public record ReviewStats(
            Range range,
            long total,
            BigDecimal average,
            @Schema(description = "So luot theo so sao 1..5, du ca muc bang 0") Map<Integer, Long> distribution,
            List<LowRatedProduct> lowestRated
    ) {}
}
