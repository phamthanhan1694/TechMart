package dev.techmart.order.service;

import dev.techmart.audit.domain.AuditAction;
import dev.techmart.audit.service.AuditLogger;

import dev.techmart.cart.service.CartService;
import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.common.outbox.OutboxRecorder;
import dev.techmart.common.web.PageResponse;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.repository.UserRepository;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.inventory.domain.InventoryReservation;
import dev.techmart.inventory.service.ReservationService;
import dev.techmart.inventory.service.VariantLocker;
import dev.techmart.order.domain.*;
import dev.techmart.order.domain.OrderStateMachine.Actor;
import dev.techmart.order.dto.OrderDtos.*;
import dev.techmart.order.repository.OrderRepository;
import dev.techmart.promotion.service.CouponService;
import dev.techmart.warranty.service.SerialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** Phí vận chuyển phẳng. Một bảng phí theo khu vực không thêm chieu sau kỹ thuật nao. */
    private static final BigDecimal FLAT_SHIPPING_FEE = new BigDecimal("30000.00");

    private final OrderRepository orders;
    private final VariantLocker variantLocker;
    private final ReservationService reservations;
    private final CartService cartService;
    private final UserRepository users;
    private final OrderCodeGenerator codeGenerator;
    private final OutboxRecorder outbox;
    private final CouponService couponService;
    private final SerialService serialService;
    private final AuditLogger audit;
    private final Clock clock;

    public OrderService(OrderRepository orders, VariantLocker variantLocker,
                        ReservationService reservations, CartService cartService,
                        UserRepository users, OrderCodeGenerator codeGenerator,
                        OutboxRecorder outbox, CouponService couponService,
                        SerialService serialService, AuditLogger audit, Clock clock) {
        this.audit = audit;
        this.orders = orders;
        this.variantLocker = variantLocker;
        this.reservations = reservations;
        this.cartService = cartService;
        this.users = users;
        this.codeGenerator = codeGenerator;
        this.outbox = outbox;
        this.couponService = couponService;
        this.serialService = serialService;
        this.clock = clock;
    }

    // =================================================================== đặt hàng

    /**
     * Đặt hàng từ một phiên checkout dạng giữ hàng.
     *
     * <p>Toàn bộ nằm trong MỘT transaction: chốt hàng đã giữ -> trừ tồn kho -> tạo đơn
     * kèm snapshot -> đánh dấu reservation da dùng -> đơn giá -> ghi outbox.
     * Bat ky buoc nao thất bại thì rollback tắt ca.
     *
     * <p><b>Idempotency:</b> đường nhanh là tra cứu theo {@code idempotencyKey}; chốt chặn
     * thật su là ràng buộc {@code UNIQUE(orders.idempotency_key)} o DB - hai request song song
     * cũng key thì một cai sẽ vì phạm ràng buộc và đọc lại kết quả của cai kia.
     *
     * <p><b>Chống oversell:</b> variant được khoá bi quan theo thứ tự id trước khi tru
     * (xem {@link VariantLocker#lockAndRefresh}).
     */
    @Transactional
    public OrderResponse placeOrder(Long userId, PlaceOrderRequest req, String idempotencyKey) {
        Optional<Order> replay = orders.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            Order existing = replay.get();
            if (!existing.isOwnedBy(userId)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            log.info("Idempotency: tra lai don {} da tao truoc do cho key {}",
                    existing.getCode(), idempotencyKey);
            return OrderResponse.from(existing);
        }

        List<InventoryReservation> held = reservations.requireOwnedSession(userId, req.sessionId());

        Map<Long, Integer> quantityByVariant = new LinkedHashMap<>();
        for (InventoryReservation r : held) {
            quantityByVariant.merge(r.getVariant().getId(), r.getQuantity(), Integer::sum);
        }

        // Khoá theo thứ tự id (chặn deadlock) và đọc lại state tươi (xem VariantLocker)
        List<ProductVariant> locked = variantLocker.lockAndRefresh(quantityByVariant.keySet());
        Map<Long, ProductVariant> lockedById = new HashMap<>();
        locked.forEach(v -> lockedById.put(v.getId(), v));

        Instant now = clock.instant();
        Order order = new Order(codeGenerator.generate(), users.getReferenceById(userId),
                req.shippingAddress().toMap(), req.note(), idempotencyKey, now);

        for (Map.Entry<Long, Integer> entry : quantityByVariant.entrySet()) {
            ProductVariant variant = lockedById.get(entry.getKey());
            if (variant == null) {
                throw BusinessException.notFound(ErrorCode.VARIANT_NOT_FOUND, entry.getKey());
            }
            try {
                // Hàng đã được giữ ở bước checkout: chuyển từ "dạng giữ" sang "đã bán"
                variant.commitHold(entry.getValue());
            } catch (IllegalStateException e) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, e.getMessage());
            }
            order.addItem(new OrderItem(variant, entry.getValue()));
        }

        // Tính tiền ở server từ order_items - không bao giờ tin số tiền client gửi lên
        order.recalculateTotals(FLAT_SHIPPING_FEE, BigDecimal.ZERO, null);
        order.logTransition(null, OrderStatus.PENDING_PAYMENT, userId, "Dat hang", now);

        Order saved;
        try {
            saved = orders.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            // Hai request song song cũng Idempotency-Key: cai thua đọc lại kết quả của cai thang
            log.info("Idempotency: race tren key {}, doc lai don da tao", idempotencyKey);
            return orders.findByIdempotencyKey(idempotencyKey)
                    .map(OrderResponse::from)
                    .orElseThrow(() -> new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
        }

        // Áp mã giảm giá SAU khi đơn có id (coupon_redemptions tham chieu order_id).
        // Trừ lượt ở đây, trong cùng transaction, có khoá dòng coupon - không phải ở
        // buoc /coupons/validate (buoc đó chỉ để hiển thị trước).
        if (req.couponCode() != null && !req.couponCode().isBlank()) {
            BigDecimal discount = couponService.redeem(
                    req.couponCode(), saved.getSubtotal(), userId, saved);
            saved.recalculateTotals(FLAT_SHIPPING_FEE, discount, req.couponCode().toUpperCase());
        }

        held.forEach(r -> r.commit(saved.getId()));
        cartService.clearAfterOrder(userId);

        outbox.recordOrderEvent(saved.getId(), OutboxRecorder.ORDER_PLACED, Map.of(
                "orderCode", saved.getCode(),
                "userId", userId,
                "totalAmount", saved.getTotalAmount().toPlainString()));

        log.info("Da tao don {} cho user {}: {} dong, tong {}",
                saved.getCode(), userId, saved.getItems().size(), saved.getTotalAmount());
        return OrderResponse.from(saved);
    }

    // =================================================================== doc

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listMyOrders(Long userId, Pageable pageable) {
        return PageResponse.from(
                orders.findByUserIdOrderByPlacedAtDesc(userId, pageable), OrderSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse getByCode(String code, AuthPrincipal principal) {
        return OrderResponse.from(requireVisibleOrder(code, principal));
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> getHistory(String code, AuthPrincipal principal) {
        requireVisibleOrder(code, principal);   // kiểm quyen trước
        return orders.findByCodeWithHistory(code)
                .map(o -> o.getHistory().stream().map(StatusHistoryResponse::from).toList())
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.ORDER_NOT_FOUND, code));
    }

    // =================================================================== đổi trạng thái

    /**
     * Huy đơn.
     *
     * <p><b>Idempotent:</b> huỷ một đơn đã huỷ trả 200 và không làm gì. Thiếu nhánh này thì
     * khách bấm huỷ hai lần sẽ được hoàn kho hai lần và tồn kho bị thổi phồng - đây là lỗi
     * phổ biến nhất trong e-commerce (docs/02-luồng-nghiep-vu.md mục 6).
     */
    @Transactional
    public OrderResponse cancel(String code, AuthPrincipal principal, CancelOrderRequest req) {
        Order order = requireVisibleOrder(code, principal);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return OrderResponse.from(order);   // không phải lỗi, và không hoàn kho lan hai
        }
        return doTransition(order, OrderStatus.CANCELLED, principal,
                req != null ? req.reason() : null);
    }

    /** Đổi trạng thái boi STAFF/ADMIN. */
    @Transactional
    public OrderResponse updateStatus(String code, AuthPrincipal principal, UpdateStatusRequest req) {
        Order order = orders.findByCode(code)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.ORDER_NOT_FOUND, code));
        OrderStatus from = order.getStatus();
        OrderResponse response = doTransition(order, req.toStatus(), principal, req.note());
        // Chỉ ghi nhật ký thao tác của nhân viên; khách tự huỷ đơn đã có order_status_history
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("orderCode", code);
        detail.put("from", from.name());
        detail.put("to", req.toStatus().name());
        if (req.note() != null && !req.note().isBlank()) detail.put("note", req.note());
        audit.record(AuditAction.ORDER_STATUS_CHANGED, "ORDER", code, detail);
        return response;
    }

    /** Đường vào cho hệ thống (webhook thanh toán, job tự động) - không có người dùng. */
    @Transactional
    public void transitionBySystem(Order order, OrderStatus toStatus, String note) {
        applyTransition(order, toStatus, Actor.SYSTEM, null, note);
    }

    private OrderResponse doTransition(Order order, OrderStatus toStatus,
                                       AuthPrincipal principal, String note) {
        Actor actor = Actor.of(principal.role());
        applyTransition(order, toStatus, actor, principal.id(), note);
        return OrderResponse.from(order);
    }

    private void applyTransition(Order order, OrderStatus toStatus, Actor actor, Long actorId, String note) {
        OrderStatus from = order.getStatus();

        if (!OrderStateMachine.transitionExists(from, toStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Khong the chuyen tu " + from + " sang " + toStatus);
        }
        if (!OrderStateMachine.isAllowed(from, toStatus, actor)) {
            // Khách có thể huỷ đơn chưa thanh toán, nhưng không được huỷ đơn da giao
            ErrorCode code = toStatus == OrderStatus.CANCELLED
                    ? ErrorCode.ORDER_NOT_CANCELLABLE : ErrorCode.ACCESS_DENIED;
            throw new BusinessException(code,
                    actor + " khong duoc phep chuyen " + from + " sang " + toStatus);
        }

        Instant now = clock.instant();
        if (OrderStateMachine.restocks(from, toStatus)) {
            restock(order);
            // Hoàn lượt mã giảm giá: không hoàn thì mã sẽ "bốc hơi" theo các đơn bị huỷ
            if (order.getCouponCode() != null) {
                couponService.releaseForOrder(order.getId());
            }
            // Hàng quay về kho: serial chuyển sang RETURNED, chờ kho kiểm tra
            serialService.releaseForOrder(order);
        }
        // Đóng gói là lúc gắn serial cho từng đơn vị hàng - không phải lúc đặt hàng.
        // Lúc đặt hàng đang tranh chấp nặng nhất, gan serial o đó sẽ phải khoá N dòng
        // thay vì một dòng đếm. Xem SerialService.assignForOrder.
        if (from == OrderStatus.PAID && toStatus == OrderStatus.PACKED) {
            serialService.assignForOrder(order);
        }
        order.applyStatus(toStatus, now);
        order.logTransition(from, toStatus, actorId, note, now);

        outbox.recordOrderEvent(order.getId(), eventTypeFor(toStatus), Map.of(
                "orderCode", order.getCode(),
                "fromStatus", from.name(),
                "toStatus", toStatus.name()));

        log.info("Don {}: {} -> {} boi {} ({})", order.getCode(), from, toStatus, actor, actorId);
    }

    /** Hoàn hàng về kho. Khoá theo thứ tự id, giống lúc trừ. */
    private void restock(Order order) {
        Map<Long, Integer> byVariant = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            byVariant.merge(item.getVariant().getId(), item.getQuantity(), Integer::sum);
        }
        for (ProductVariant variant : variantLocker.lockAndRefresh(byVariant.keySet())) {
            variant.restock(byVariant.get(variant.getId()));
        }
        log.info("Da hoan kho cho don {}: {} variant", order.getCode(), byVariant.size());
    }

    private static String eventTypeFor(OrderStatus status) {
        return switch (status) {
            case PAID -> OutboxRecorder.ORDER_PAID;
            case CANCELLED -> OutboxRecorder.ORDER_CANCELLED;
            case SHIPPED -> OutboxRecorder.ORDER_SHIPPED;
            case DELIVERED -> OutboxRecorder.ORDER_DELIVERED;
            default -> "ORDER_" + status.name();
        };
    }

    /**
     * Đơn không thuộc user hiện tại trả 404 chứ không 403.
     * ADMIN và STAFF xem được mọi đơn.
     */
    private Order requireVisibleOrder(String code, AuthPrincipal principal) {
        Order order = orders.findByCode(code)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.ORDER_NOT_FOUND, code));
        if (principal.role() == Role.CUSTOMER && !order.isOwnedBy(principal.id())) {
            throw BusinessException.notFound(ErrorCode.ORDER_NOT_FOUND, code);
        }
        return order;
    }
}
