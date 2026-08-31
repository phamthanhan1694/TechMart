package dev.techmart.inventory.service;

import dev.techmart.cart.domain.Cart;
import dev.techmart.cart.domain.CartItem;
import dev.techmart.cart.repository.CartRepository;
import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.common.config.AppProperties;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.repository.UserRepository;
import dev.techmart.inventory.domain.InventoryReservation;
import dev.techmart.inventory.dto.InventoryDtos.CheckoutSessionResponse;
import dev.techmart.inventory.dto.InventoryDtos.ReservedLine;
import dev.techmart.inventory.dto.InventoryDtos.StockShortage;
import dev.techmart.inventory.repository.InventoryReservationRepository;
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
 * Giu hang co han dung khi khach vao buoc thanh toan.
 *
 * <p>Bai toan: khach vao trang thanh toan, dang nhap dia chi thi hang bi nguoi khac mua mat
 * -> bao "het hang" giua chung, trai nghiem te va mat don.
 *
 * <p>Cach giai: tao ban ghi giu hang trong 10 phut. {@code available = stock - reserved}.
 * Het han ma chua dat hang thi job {@link ReservationExpiryJob} nha ra.
 *
 * <p>Xem ADR-0004 va docs/02-luong-nghiep-vu.md muc 2.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final InventoryReservationRepository reservations;
    private final VariantLocker variantLocker;
    private final CartRepository carts;
    private final UserRepository users;
    private final AppProperties props;
    private final Clock clock;

    public ReservationService(InventoryReservationRepository reservations,
                              VariantLocker variantLocker,
                              CartRepository carts,
                              UserRepository users,
                              AppProperties props,
                              Clock clock) {
        this.reservations = reservations;
        this.variantLocker = variantLocker;
        this.carts = carts;
        this.users = users;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Giu hang cho toan bo gio hang cua user.
     *
     * <p>Khoa bi quan tat ca variant lien quan, <b>theo thu tu id</b> - xem
     * {@link VariantLocker#lockAndRefresh}. Kiem du hang cho TAT CA truoc,
     * neu thieu bat ky mon nao thi nem loi va rollback toan bo: giu duoc mot nua gio hang
     * la trang thai vo nghia voi khach.
     */
    @Transactional
    public CheckoutSessionResponse hold(Long userId) {
        // Chu y: query nay KHONG fetch variant - variant phai o dang proxy cho toi khi
        // cau query FOR UPDATE nap no. Xem VariantLocker.
        Cart cart = carts.findByUserIdForCheckout(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_EMPTY));
        if (cart.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (CartItem item : cart.getItems()) {
            wanted.merge(item.getVariant().getId(), item.getQuantity(), Integer::sum);
        }

        // Khoa theo thu tu id (chan deadlock) VA doc lai state tuoi (chan optimistic lock gia)
        List<ProductVariant> locked = variantLocker.lockAndRefresh(wanted.keySet());
        if (locked.size() != wanted.size()) {
            throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND,
                    "Mot so san pham trong gio khong con ton tai");
        }

        // Kiem du hang cho TAT CA truoc khi thay doi bat cu thu gi
        List<StockShortage> shortages = new ArrayList<>();
        for (ProductVariant v : locked) {
            int need = wanted.get(v.getId());
            if (v.availableQuantity() < need) {
                shortages.add(new StockShortage(v.getId(), v.getSku(), v.getProduct().getName(),
                        need, v.availableQuantity()));
            }
        }
        if (!shortages.isEmpty()) {
            throw BusinessException.insufficientStock(shortages);
        }

        String sessionId = UUID.randomUUID().toString();
        Instant expiresAt = clock.instant().plus(props.inventory().reservationTtl());
        var user = users.getReferenceById(userId);

        List<ReservedLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (ProductVariant v : locked) {
            int quantity = wanted.get(v.getId());
            v.hold(quantity);   // tang reserved_quantity - bo dem phi chuan hoa
            reservations.save(new InventoryReservation(v, user, sessionId, quantity, expiresAt));

            BigDecimal lineTotal = v.getPrice().multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(lineTotal);
            lines.add(new ReservedLine(v.getId(), v.getSku(), v.getProduct().getName(),
                    v.getName(), v.getPrice(), quantity, lineTotal));
        }

        log.info("Da giu hang phien {} cho user {}: {} dong, het han luc {}",
                sessionId, userId, lines.size(), expiresAt);

        return new CheckoutSessionResponse(sessionId, expiresAt,
                secondsRemaining(expiresAt), lines, subtotal);
    }

    @Transactional(readOnly = true)
    public CheckoutSessionResponse getSession(Long userId, String sessionId) {
        // Luong DOC: nap kem variant + product de hien thi
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

    /** Khach chu dong huy phien checkout - nha hang ngay, khong cho het han. */
    @Transactional
    public void cancel(Long userId, String sessionId) {
        List<InventoryReservation> held = requireOwnedSession(userId, sessionId);

        Map<Long, Integer> byVariant = new LinkedHashMap<>();
        for (InventoryReservation r : held) {
            byVariant.merge(r.getVariant().getId(), r.getQuantity(), Integer::sum);
        }
        // Khoa truoc khi doi ton kho, giong het luong giu hang
        for (ProductVariant v : variantLocker.lockAndRefresh(byVariant.keySet())) {
            v.releaseHold(byVariant.get(v.getId()));
        }
        held.forEach(InventoryReservation::release);

        log.info("User {} da huy phien giu hang {}", userId, sessionId);
    }

    /**
     * Lay cac reservation con hieu luc cua mot phien, de OrderService chot don.
     * Kiem ca quyen so huu va han dung tai day.
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
        // Phien cua nguoi khac -> 404, khong phai 403
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
