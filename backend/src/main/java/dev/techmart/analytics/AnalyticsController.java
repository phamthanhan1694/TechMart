package dev.techmart.analytics;

import dev.techmart.analytics.AnalyticsDtos.*;
import dev.techmart.order.domain.OrderStatus;
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
@Tag(name = "Admin - Thong ke", description = "KPI co so sanh ky truoc, chuoi thoi gian, co cau, pheu, ban do nhiet, xuat CSV")
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService service;
    private final OrderCsvExporter exporter;

    public AnalyticsController(AnalyticsService service, OrderCsvExporter exporter) {
        this.service = service;
        this.exporter = exporter;
    }

    @GetMapping("/overview")
    @Operation(summary = "KPI tong quan kem so sanh ky truoc cung do dai",
            description = "changePercent = null khi ky truoc bang 0 - khong tu bia ra '+100%'.")
    public Overview overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.overview(from, to);
    }

    @GetMapping("/timeseries")
    @Operation(summary = "Chuoi thoi gian day du moc, ke ca moc bang 0",
            description = "groupBy: day | week (bat dau Thu Hai) | month. Kem chuoi ky truoc de ve duong so sanh.")
    public TimeSeries timeseries(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(defaultValue = "day") String groupBy) {
        return service.series(from, to, groupBy);
    }

    @GetMapping("/breakdown")
    @Operation(summary = "Co cau doanh so",
            description = "dimension: category | brand | province. Qua `limit` dong thi phan duoi gop thanh 'Khac'.")
    public Breakdown breakdown(@RequestParam(defaultValue = "category") String dimension,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(defaultValue = "8") int limit) {
        return service.breakdown(dimension, from, to, limit);
    }

    @GetMapping("/funnel")
    @Operation(summary = "Pheu don hang tu lich su trang thai", description = "Kem trung vi thoi gian thanh toan va giao hang.")
    public Funnel funnel(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.funnel(from, to);
    }

    @GetMapping("/heatmap")
    @Operation(summary = "Don theo thu x gio (gio Viet Nam)", description = "Chi tra o co du lieu; client tu dien o trong.")
    public Heatmap heatmap(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.heatmap(from, to);
    }

    @GetMapping("/customers")
    @Operation(summary = "Khach moi, khach quay lai, khach chi tieu nhieu nhat")
    public CustomerStats customers(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(defaultValue = "10") int limit) {
        return service.customers(from, to, limit);
    }

    @GetMapping("/payments")
    @Operation(summary = "Thanh toan theo phuong thuc", description = "Ti le thanh cong tinh tren giao dich da co ket qua.")
    public List<PaymentMethodStats> payments(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.payments(from, to);
    }

    @GetMapping("/coupons")
    @Operation(summary = "Hieu qua ma giam gia")
    public CouponStats coupons(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.coupons(from, to);
    }

    @GetMapping("/inventory")
    @Operation(summary = "Suc khoe ton kho (anh chup hien tai)",
            description = "Het hang / sap het / on dinh, gia tri ton theo gia ban, hang cham ban trong slowDays ngay.")
    public InventoryStats inventory(@RequestParam(defaultValue = "10") int lowThreshold,
                                    @RequestParam(defaultValue = "30") int slowDays) {
        return service.inventory(lowThreshold, slowDays);
    }

    @GetMapping("/reviews")
    @Operation(summary = "Phan bo danh gia va san pham bi cham thap nhat (toi thieu 3 luot)")
    public ReviewStats reviews(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.reviews(from, to);
    }

    @GetMapping("/export/orders.csv")
    @Operation(summary = "Xuat don hang ra CSV",
            description = "Stream tung dong, UTF-8 co BOM cho Excel. Qua 100.000 dong tra 422 EXPORT_TOO_LARGE. Moi lan xuat duoc ghi audit log.")
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
