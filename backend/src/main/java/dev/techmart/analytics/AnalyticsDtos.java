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

    @Schema(description = "Khoảng ngày, tính cả hai đầu, theo múi giờ báo cáo")
    public record Range(LocalDate from, LocalDate to, int days) {}

    @Schema(description = "Một chỉ số kèm kỳ trước cùng độ dài để so sánh")
    public record Kpi(
            BigDecimal current,
            BigDecimal previous,
            @Schema(description = "% thay đổi so với kỳ trước; null khi kỳ trước bằng 0 (không chia được)")
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
            @Schema(description = "Tỉ lệ huỷ trên tổng đơn đặt, đơn vị %") Kpi cancelRate,
            Kpi discountTotal,
            Kpi newCustomers,
            Kpi buyers,
            @Schema(description = "% người mua trong kỳ đã từng mua trước kỳ, đơn vị %") Kpi returningBuyerRate
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
            @Schema(description = "Kỳ trước cùng độ dài, căn theo chỉ số - để vẽ đường so sánh")
            List<SeriesPoint> previous
    ) {}

    public record BreakdownRow(
            String key,
            String label,
            BigDecimal revenue,
            long orders,
            long units,
            @Schema(description = "Tỉ trọng doanh số, đơn vị %") BigDecimal share
    ) {}

    public record Breakdown(
            String dimension,
            @Schema(description = "category/brand: doanh số hàng hoá (tổng line_total, trước giảm giá và phí ship). "
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
            @Schema(description = "Trung vị số giờ từ đặt đến thanh toán") BigDecimal medianHoursToPay,
            @Schema(description = "Trung vị số giờ từ đặt đến giao thành công") BigDecimal medianHoursToDeliver
    ) {}

    public record HeatCell(
            @Schema(description = "1 = Thứ Hai ... 7 = Chủ Nhật (ISO)") int dayOfWeek,
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
            @Schema(description = "Tồn kho x giá bán hiện tại - không có giá vốn nên đây KHÔNG phải giá trị sổ sách")
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
            @Schema(description = "Số lượt theo số sao 1..5, đủ cả mức bằng 0") Map<Integer, Long> distribution,
            List<LowRatedProduct> lowestRated
    ) {}
}
