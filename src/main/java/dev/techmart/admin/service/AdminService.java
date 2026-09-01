package dev.techmart.admin.service;

import dev.techmart.admin.dto.AdminDtos.*;
import dev.techmart.admin.repository.DashboardRepository;
import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.catalog.repository.ProductVariantRepository;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.common.web.PageResponse;
import dev.techmart.order.domain.Order;
import dev.techmart.order.domain.OrderItem;
import dev.techmart.order.domain.OrderStatus;
import dev.techmart.order.repository.OrderRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Service
public class AdminService {

    private static final int LOW_STOCK_THRESHOLD = 10;
    /** Whitelist: gia tri groupBy tu client khong bao gio duoc ghep thang vao SQL. */
    private static final Set<String> ALLOWED_GROUP_BY = Set.of("day", "month");

    private final DashboardRepository dashboard;
    private final OrderRepository orders;
    private final ProductVariantRepository variants;
    private final Clock clock;

    public AdminService(DashboardRepository dashboard, OrderRepository orders,
                        ProductVariantRepository variants, Clock clock) {
        this.dashboard = dashboard;
        this.orders = orders;
        this.variants = variants;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderRow> listOrders(OrderStatus status, LocalDate from, LocalDate to,
                                                  Pageable pageable) {
        Instant fromInstant = from != null ? DashboardRepository.startOfDayUtc(from) : null;
        Instant toInstant = to != null ? DashboardRepository.startOfDayUtc(to.plusDays(1)) : null;

        return PageResponse.from(
                orders.findForAdmin(status, fromInstant, toInstant, pageable),
                AdminService::toRow);
    }

    @Transactional(readOnly = true)
    public RevenueReport revenue(String groupBy, LocalDate from, LocalDate to) {
        String unit = ALLOWED_GROUP_BY.contains(groupBy) ? groupBy : "day";
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate start = from != null ? from : today.minusDays(30);
        LocalDate end = to != null ? to : today;

        List<RevenuePoint> points = dashboard.revenueByPeriod(unit,
                DashboardRepository.startOfDayUtc(start),
                DashboardRepository.startOfDayUtc(end.plusDays(1)));

        BigDecimal total = points.stream()
                .map(RevenuePoint::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long orderCount = points.stream().mapToLong(RevenuePoint::orderCount).sum();

        return new RevenueReport(unit, start, end, total, orderCount, points);
    }

    @Transactional(readOnly = true)
    public List<TopProduct> topProducts(LocalDate from, LocalDate to, int limit) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate start = from != null ? from : today.minusDays(30);
        LocalDate end = to != null ? to : today;
        return dashboard.topProducts(
                DashboardRepository.startOfDayUtc(start),
                DashboardRepository.startOfDayUtc(end.plusDays(1)),
                Math.min(Math.max(limit, 1), 50));
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        Instant startOfToday = DashboardRepository.startOfDayUtc(today);
        Instant startOfTomorrow = DashboardRepository.startOfDayUtc(today.plusDays(1));
        Instant startOfMonth = DashboardRepository.startOfDayUtc(today.withDayOfMonth(1));

        BigDecimal revenueMonth = dashboard.revenueBetween(startOfMonth, startOfTomorrow);
        long ordersMonth = dashboard.countOrdersBetween(startOfMonth, startOfTomorrow);
        BigDecimal average = ordersMonth == 0 ? BigDecimal.ZERO
                : revenueMonth.divide(BigDecimal.valueOf(ordersMonth), 2, RoundingMode.HALF_UP);

        return new DashboardSummary(
                dashboard.revenueBetween(startOfToday, startOfTomorrow),
                revenueMonth,
                dashboard.countOrdersBetween(startOfToday, startOfTomorrow),
                ordersMonth,
                average,
                dashboard.countByStatus(),
                dashboard.countLowStockVariants(LOW_STOCK_THRESHOLD),
                dashboard.countPendingOutbox(),
                dashboard.countActiveReservations());
    }

    @Transactional(readOnly = true)
    public List<LowStockItem> lowStock(int threshold) {
        return variants.findLowStock(threshold).stream()
                .map(AdminService::toLowStock)
                .toList();
    }

    @Transactional(readOnly = true)
    public Order requireOrder(String code) {
        return orders.findByCode(code)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.ORDER_NOT_FOUND, code));
    }

    private static AdminOrderRow toRow(Order o) {
        return new AdminOrderRow(o.getCode(), o.getUser().getEmail(), o.getStatus(),
                o.getTotalAmount(), o.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
                o.getPlacedAt());
    }

    private static LowStockItem toLowStock(ProductVariant v) {
        return new LowStockItem(v.getId(), v.getSku(), v.getProduct().getName(), v.getName(),
                v.getStockQuantity(), v.getReservedQuantity(), v.availableQuantity());
    }
}
