package dev.techmart.analytics;

import dev.techmart.analytics.AnalyticsDtos.*;
import dev.techmart.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Thống kê cho ADMIN. Mọi khoảng ngày tính theo giờ Việt Nam, mặc định 30 ngày gần nhất,
 * tối đa 366 ngày. STAFF không xem được: doanh thu là số liệu kinh doanh nhạy cảm.
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@Tag(name = "Admin - Thống kê", description = "KPI có so sánh kỳ trước, chuỗi thời gian, cơ cấu, phễu, bản đồ nhiệt, xuất CSV")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService service;
    private final OrderCsvExporter exporter;

    @GetMapping("/overview")
    @Operation(summary = "KPI tổng quan kèm so sánh kỳ trước cùng độ dài",
            description = "changePercent = null khi kỳ trước bằng 0 - không tự bịa ra '+100%'.")
    public Overview overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.overview(from, to);
    }

    @GetMapping("/timeseries")
    @Operation(summary = "Chuỗi thời gian đầy đủ mốc, kể cả mốc bằng 0",
            description = "groupBy: day | week (bắt đầu Thứ Hai) | month. Kèm chuỗi kỳ trước để vẽ đường so sánh.")
    public TimeSeries timeseries(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(defaultValue = "day") String groupBy) {
        return service.series(from, to, groupBy);
    }

    @GetMapping("/breakdown")
    @Operation(summary = "Cơ cấu doanh số",
            description = "dimension: category | brand | province. Qua `limit` dòng thì phần đuôi gộp thành 'Khác'.")
    public Breakdown breakdown(@RequestParam(defaultValue = "category") String dimension,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(defaultValue = "8") int limit) {
        return service.breakdown(dimension, from, to, limit);
    }

    @GetMapping("/funnel")
    @Operation(summary = "Phễu đơn hàng từ lịch sử trạng thái", description = "Kèm trung vị thời gian thanh toán và giao hàng.")
    public Funnel funnel(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.funnel(from, to);
    }

    @GetMapping("/heatmap")
    @Operation(summary = "Đơn theo thứ x giờ (giờ Việt Nam)", description = "Chỉ trả ô có dữ liệu; client tự điền ô trống.")
    public Heatmap heatmap(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.heatmap(from, to);
    }

    @GetMapping("/customers")
    @Operation(summary = "Khách mới, khách quay lại, khách chi tiêu nhiều nhất")
    public CustomerStats customers(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(defaultValue = "10") int limit) {
        return service.customers(from, to, limit);
    }

    @GetMapping("/payments")
    @Operation(summary = "Thanh toán theo phương thức", description = "Tỉ lệ thành công tính trên giao dịch đã có kết quả.")
    public List<PaymentMethodStats> payments(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.payments(from, to);
    }

    @GetMapping("/coupons")
    @Operation(summary = "Hiệu quả mã giảm giá")
    public CouponStats coupons(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.coupons(from, to);
    }

    @GetMapping("/inventory")
    @Operation(summary = "Sức khoẻ tồn kho (ảnh chụp hiện tại)",
            description = "Hết hàng / sắp hết / ổn định, giá trị tồn theo giá bán, hàng chậm bán trong slowDays ngày.")
    public InventoryStats inventory(@RequestParam(defaultValue = "10") int lowThreshold,
                                    @RequestParam(defaultValue = "30") int slowDays) {
        return service.inventory(lowThreshold, slowDays);
    }

    @GetMapping("/reviews")
    @Operation(summary = "Phân bố đánh giá và sản phẩm bị chấm thấp nhất (tối thiểu 3 lượt)")
    public ReviewStats reviews(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.reviews(from, to);
    }

    @GetMapping("/export/orders.csv")
    @Operation(summary = "Xuất đơn hàng ra CSV",
            description = "Stream từng dòng, UTF-8 có BOM cho Excel. Qua 100.000 dòng trả 422 EXPORT_TOO_LARGE. Mỗi lần xuất được ghi audit log.")
    public void exportOrders(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @RequestParam(required = false) OrderStatus status,
                             HttpServletResponse response) throws IOException {
        // Kiểm trước, rồi mới đặt header tải file: lỗi 422 lúc này vẫn trả về JSON bình thường
        OrderCsvExporter.ExportPlan plan = exporter.plan(from, to, status);

        String filename = "don-hang_" + plan.range().from() + "_" + plan.range().to() + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        response.setHeader("X-Total-Rows", String.valueOf(plan.rows()));
        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        exporter.write(plan, writer);
        writer.flush();
    }
}
