package dev.techmart.order.service;

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

    /** Phi van chuyen phang. Mot bang phi theo khu vuc khong them chieu sau ky thuat nao. */
    private static final BigDecimal FLAT_SHIPPING_FEE = new BigDecimal("30000.00");

    private final OrderRepository orders;
    private final VariantLocker variantLocker;
    private final ReservationService reservations;
    private final CartService cartService;
    private final UserRepository users;
    private final OrderCodeGenerator codeGenerator;
    private final OutboxRecorder outbox;
    private final CouponService couponService;
    private final Clock clock;

    public OrderService(OrderRepository orders, VariantLocker variantLocker,
                        ReservationService reservations, CartService cartService,
                        UserRepository users, OrderCodeGenerator codeGenerator,
                        OutboxRecorder outbox, CouponService couponService, Clock clock) {
        this.orders = orders;
        this.variantLocker = variantLocker;
        this.reservations = reservations;
        this.cartService = cartService;
        this.users = users;
        this.codeGenerator = codeGenerator;
        this.outbox = outbox;
        this.couponService = couponService;
        this.clock = clock;
    }

    // =================================================================== dat hang

    /**
     * Dat hang tu mot phien checkout dang giu hang.
     *
     * <p>Toan bo nam trong MOT transaction: chot hang da giu -> tru ton kho -> tao don
     * kem snapshot -> danh dau reservation da dung -> don gio -> ghi outbox.
     * Bat ky buoc nao that bai thi rollback tat ca.
     *
     * <p><b>Idempotency:</b> duong nhanh la tra cuu theo {@code idempotencyKey}; chot chan
     * that su la rang buoc {@code UNIQUE(orders.idempotency_key)} o DB - hai request song song
     * cung key thi mot cai se vi pham rang buoc va doc lai ket qua cua cai kia.
     *
     * <p><b>Chong oversell:</b> variant duoc khoa bi quan theo thu tu id truoc khi tru
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

        // Khoa theo thu tu id (chan deadlock) VA doc lai state tuoi (xem VariantLocker)
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
                // Hang da duoc giu o buoc checkout: chuyen tu "dang giu" sang "da ban"
                variant.commitHold(entry.getValue());
            } catch (IllegalStateException e) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, e.getMessage());
            }
            order.addItem(new OrderItem(variant, entry.getValue()));
        }

        // Tinh tien o server tu order_items - khong bao gio tin so tien client gui len
        order.recalculateTotals(FLAT_SHIPPING_FEE, BigDecimal.ZERO, null);
        order.logTransition(null, OrderStatus.PENDING_PAYMENT, userId, "Dat hang", now);

        Order saved;
        try {
            saved = orders.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            // Hai request song song cung Idempotency-Key: cai thua doc lai ket qua cua cai thang
            log.info("Idempotency: race tren key {}, doc lai don da tao", idempotencyKey);
            return orders.findByIdempotencyKey(idempotencyKey)
                    .map(OrderResponse::from)
                    .orElseThrow(() -> new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
        }

        // Ap ma giam gia SAU khi don co id (coupon_redemptions tham chieu order_id).
        // Tru luot o day, trong cung transaction, co khoa dong coupon - khong phai o
        // buoc /coupons/validate (buoc do chi de hien thi truoc).
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
        requireVisibleOrder(code, principal);   // kiem quyen truoc
        return orders.findByCodeWithHistory(code)
                .map(o -> o.getHistory().stream().map(StatusHistoryResponse::from).toList())
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.ORDER_NOT_FOUND, code));
    }

    // =================================================================== doi trang thai

    /**
     * Huy don.
     *
     * <p><b>Idempotent:</b> huy mot don da huy tra 200 va khong lam gi. Thieu nhanh nay thi
     * khach bam huy hai lan se duoc hoan kho hai lan va ton kho bi thoi phong - day la loi
     * pho bien nhat trong e-commerce (docs/02-luong-nghiep-vu.md muc 6).
     */
    @Transactional
    public OrderResponse cancel(String code, AuthPrincipal principal, CancelOrderRequest req) {
        Order order = requireVisibleOrder(code, principal);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return OrderResponse.from(order);   // khong phai loi, va khong hoan kho lan hai
        }
        return doTransition(order, OrderStatus.CANCELLED, principal,
                req != null ? req.reason() : null);
    }

    /** Doi trang thai boi STAFF/ADMIN. */
    @Transactional
    public OrderResponse updateStatus(String code, AuthPrincipal principal, UpdateStatusRequest req) {
        Order order = orders.findByCode(code)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.ORDER_NOT_FOUND, code));
        return doTransition(order, req.toStatus(), principal, req.note());
    }

    /** Duong vao cho he thong (webhook thanh toan, job tu dong) - khong co nguoi dung. */
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
            // Khach co the huy don chua thanh toan, nhung khong duoc huy don da giao
            ErrorCode code = toStatus == OrderStatus.CANCELLED
                    ? ErrorCode.ORDER_NOT_CANCELLABLE : ErrorCode.ACCESS_DENIED;
            throw new BusinessException(code,
                    actor + " khong duoc phep chuyen " + from + " sang " + toStatus);
        }

        Instant now = clock.instant();
        if (OrderStateMachine.restocks(from, toStatus)) {
            restock(order);
            // Hoan luot ma giam gia: khong hoan thi ma se "boc hoi" theo cac don bi huy
            if (order.getCouponCode() != null) {
                couponService.releaseForOrder(order.getId());
            }
        }
        order.applyStatus(toStatus, now);
        order.logTransition(from, toStatus, actorId, note, now);

        outbox.recordOrderEvent(order.getId(), eventTypeFor(toStatus), Map.of(
                "orderCode", order.getCode(),
                "fromStatus", from.name(),
                "toStatus", toStatus.name()));

        log.info("Don {}: {} -> {} boi {} ({})", order.getCode(), from, toStatus, actor, actorId);
    }

    /** Hoan hang ve kho. Khoa theo thu tu id, giong luc tru. */
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
     * Don khong thuoc user hien tai tra 404 chu khong 403.
     * ADMIN va STAFF xem duoc moi don.
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
