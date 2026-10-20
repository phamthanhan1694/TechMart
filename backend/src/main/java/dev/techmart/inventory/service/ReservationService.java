package dev.techmart.inventory.service;

import dev.techmart.cart.domain.Cart;
import dev.techmart.cart.domain.CartItem;
import dev.techmart.cart.repository.CartRepository;
import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.common.config.AppProperties;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.common.observability.BusinessMetrics;
import dev.techmart.identity.repository.UserRepository;
import dev.techmart.inventory.domain.InventoryReservation;
import dev.techmart.inventory.dto.InventoryDtos.CheckoutSessionResponse;
import dev.techmart.inventory.dto.InventoryDtos.ReservedLine;
import dev.techmart.inventory.dto.InventoryDtos.StockShortage;
import dev.techmart.inventory.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Giữ hàng có hạn dùng khi khách vào bước thanh toán.
 *
 * <p>Bài toán: khách vào trang thanh toán, đang nhập địa chỉ thì hàng bị người khác mua mất
 * -> báo "hết hàng" giữa chừng, trải nghiệm tệ và mất đơn.
 *
 * <p>Cách giải: tạo bản ghi giữ hàng trong 10 phút. {@code available = stock - reserved}.
 * Hết hạn mà chưa đặt hàng thì job {@link ReservationExpiryJob} nhả ra.
 *
 * <p>Xem ADR-0004 và docs/02-luồng-nghiep-vu.md mục 2.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final InventoryReservationRepository reservations;
    private final VariantLocker variantLocker;
    private final CartRepository carts;
    private final UserRepository users;
    private final AppProperties props;
    private final BusinessMetrics metrics;
    private final Clock clock;

    /**
     * Giữ hàng cho toàn bộ giỏ hàng của user.
     *
     * <p>Khoá bi quan tất cả variant liên quan, <b>theo thứ tự id</b> - xem
     * {@link VariantLocker#lockAndRefresh}. Kiểm đủ hàng cho TẤT CẢ trước,
     * nếu thiếu bất kỳ món nào thì ném lỗi và rollback toàn bộ: giữ được một nửa giỏ hàng
     * là trạng thái vô nghĩa với khách.
     */
    @Transactional
    public CheckoutSessionResponse hold(Long userId) {
        // Chú ý: query này KHÔNG fetch variant - variant phải ở dạng proxy cho tới khi
        // câu query FOR UPDATE nạp nó. Xem VariantLocker.
        Cart cart = carts.findByUserIdForCheckout(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_EMPTY));
        if (cart.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (CartItem item : cart.getItems()) {
            wanted.merge(item.getVariant().getId(), item.getQuantity(), Integer::sum);
        }

        // Khoá theo thứ tự id (chặn deadlock) và đọc lại state tươi (chặn optimistic lock giả)
        List<ProductVariant> locked = variantLocker.lockAndRefresh(wanted.keySet());
        if (locked.size() != wanted.size()) {
            throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND,
                    "Một số sản phẩm trong giỏ không còn tồn tại");
        }

        // Kiểm đủ hàng cho TẤT CẢ trước khi thay đổi bất cứ thứ gì
        List<StockShortage> shortages = new ArrayList<>();
        for (ProductVariant v : locked) {
            int need = wanted.get(v.getId());
            if (v.availableQuantity() < need) {
                shortages.add(new StockShortage(v.getId(), v.getSku(), v.getProduct().getName(),
                        need, v.availableQuantity()));
            }
        }
        if (!shortages.isEmpty()) {
            metrics.recordInsufficientStock();
            throw BusinessException.insufficientStock(shortages);
        }

        String sessionId = UUID.randomUUID().toString();
        Instant expiresAt = clock.instant().plus(props.inventory().reservationTtl());
        var user = users.getReferenceById(userId);

        List<ReservedLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (ProductVariant v : locked) {
            int quantity = wanted.get(v.getId());
            v.hold(quantity);   // tầng reserved_quantity - bộ đếm phi chuẩn hoá
            reservations.save(new InventoryReservation(v, user, sessionId, quantity, expiresAt));

            BigDecimal lineTotal = v.getPrice().multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(lineTotal);
            lines.add(new ReservedLine(v.getId(), v.getSku(), v.getProduct().getName(),
                    v.getName(), v.getPrice(), quantity, lineTotal));
        }

        log.info("Đã giữ hàng phiên {} cho user {}: {} dòng, hết hạn lúc {}",
                sessionId, userId, lines.size(), expiresAt);

        return new CheckoutSessionResponse(sessionId, expiresAt,
                secondsRemaining(expiresAt), lines, subtotal);
    }

    @Transactional(readOnly = true)
    public CheckoutSessionResponse getSession(Long userId, String sessionId) {
        // Luồng ĐỌC: nạp kèm variant + product để hiển thị
        List<InventoryReservation> held = checkSession(
                userId, sessionId, reservations.findBySessionIdWithVariant(sessionId));

        BigDecimal subtotal = BigDecimal.ZERO;
        List<ReservedLine> lines = new ArrayList<>();
        for (InventoryReservation r : held) {
            ProductVariant v = r.getVariant();
            BigDecimal lineTotal = v.getPrice().multiply(BigDecimal.valueOf(r.getQuantity()));
            subtotal = subtotal.add(lineTotal);
            lines.add(new ReservedLine(v.getId(), v.getSku(), v.getProduct().getName(),
                    v.getName(), v.getPrice(), r.getQuantity(), lineTotal));
        }
        Instant expiresAt = held.get(0).getExpiresAt();
        return new CheckoutSessionResponse(sessionId, expiresAt, secondsRemaining(expiresAt), lines, subtotal);
    }

    /** Khách chủ động huỷ phiên checkout - nhả hàng ngay, không chờ hết hạn. */
    @Transactional
    public void cancel(Long userId, String sessionId) {
        List<InventoryReservation> held = requireOwnedSession(userId, sessionId);

        Map<Long, Integer> byVariant = new LinkedHashMap<>();
        for (InventoryReservation r : held) {
            byVariant.merge(r.getVariant().getId(), r.getQuantity(), Integer::sum);
        }
        // Khoá trước khi đổi tồn kho, giống hệt luồng giữ hàng
        for (ProductVariant v : variantLocker.lockAndRefresh(byVariant.keySet())) {
            v.releaseHold(byVariant.get(v.getId()));
        }
        held.forEach(InventoryReservation::release);

        log.info("User {} đã huỷ phiên giữ hàng {}", userId, sessionId);
    }

    /**
     * Lấy các reservation còn hiệu lực của một phiên, để OrderService chốt đơn.
     * Kiểm cả quyền sở hữu và hạn dùng tại đây.
     */
    @Transactional(readOnly = true)
    public List<InventoryReservation> requireOwnedSession(Long userId, String sessionId) {
        return checkSession(userId, sessionId, reservations.findBySessionId(sessionId));
    }

    private List<InventoryReservation> checkSession(Long userId, String sessionId,
                                                    List<InventoryReservation> all) {
        if (all.isEmpty()) {
            throw BusinessException.notFound(ErrorCode.RESERVATION_NOT_FOUND, sessionId);
        }
        // Phiên của người khác -> 404, không phải 403
        if (!all.get(0).getUser().getId().equals(userId)) {
            throw BusinessException.notFound(ErrorCode.RESERVATION_NOT_FOUND, sessionId);
        }
        List<InventoryReservation> held = all.stream().filter(InventoryReservation::isHeld).toList();
        if (held.isEmpty()) {
            throw new BusinessException(ErrorCode.RESERVATION_EXPIRED);
        }
        if (held.get(0).isExpired(clock.instant())) {
            throw new BusinessException(ErrorCode.RESERVATION_EXPIRED);
        }
        return held;
    }

    private long secondsRemaining(Instant expiresAt) {
        return Math.max(0, Duration.between(clock.instant(), expiresAt).toSeconds());
    }
}
