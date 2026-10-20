package dev.techmart.flashsale.service;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.common.outbox.OutboxRecorder;
import dev.techmart.flashsale.domain.FlashSaleItem;
import dev.techmart.flashsale.dto.FlashSaleDtos.PurchaseRequest;
import dev.techmart.flashsale.repository.FlashSaleItemRepository;
import dev.techmart.identity.repository.UserRepository;
import dev.techmart.inventory.service.VariantLocker;
import dev.techmart.order.domain.Order;
import dev.techmart.order.domain.OrderItem;
import dev.techmart.order.domain.OrderStatus;
import dev.techmart.order.dto.OrderDtos.OrderResponse;
import dev.techmart.order.repository.OrderRepository;
import dev.techmart.order.service.OrderCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Ghi "sổ cái" cho một suất flash sale đã thắng ở cửa Redis.
 *
 * <p><b>Vì sao tách thành bean riêng thay vì để trong FlashSaleService:</b> Spring AOP hoạt động
 * qua proxy, nên một method gọi sang method {@code @Transactional} <i>trong cùng class</i>
 * sẽ không đi qua proxy và annotation bị bỏ qua hoàn toàn. Bước gọi Redis phải nằm ngoài
 * transaction, còn bước ghi DB phải nằm trong - hai yêu cầu này chỉ thoả mãn được khi
 * chúng ở hai bean khác nhau.
 *
 * <p>Cùng một bẫy đã làm job nhả hàng quá hạn ném TransactionRequiredException mỗi 30 giây
 * ở giai đoạn trước.
 */
@Component
public class FlashSaleOrderWriter {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleOrderWriter.class);
    private static final BigDecimal FLAT_SHIPPING_FEE = new BigDecimal("30000.00");

    private final FlashSaleItemRepository items;
    private final VariantLocker variantLocker;
    private final OrderRepository orders;
    private final OrderCodeGenerator codeGenerator;
    private final UserRepository users;
    private final OutboxRecorder outbox;
    private final Clock clock;

    public FlashSaleOrderWriter(FlashSaleItemRepository items, VariantLocker variantLocker,
                                OrderRepository orders, OrderCodeGenerator codeGenerator,
                                UserRepository users, OutboxRecorder outbox, Clock clock) {
        this.items = items;
        this.variantLocker = variantLocker;
        this.orders = orders;
        this.codeGenerator = codeGenerator;
        this.users = users;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse write(Long userId, Long itemId, PurchaseRequest req, String idempotencyKey) {
        var replay = orders.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            return OrderResponse.from(replay.get());
        }

        // Khoá suất bán trước, rồi khoá variant - luôn cùng một thứ tự để không deadlock
        FlashSaleItem item = items.lockById(itemId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.SOLD_OUT, itemId));
        ProductVariant variant = variantLocker.lockAndRefresh(List.of(item.getVariant().getId()))
                .stream().findFirst()
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.VARIANT_NOT_FOUND, itemId));

        try {
            variant.deduct(req.quantity());
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, e.getMessage());
        }
        item.recordSold(req.quantity());

        Instant now = clock.instant();
        Order order = new Order(codeGenerator.generate(), users.getReferenceById(userId),
                req.shippingAddress().toMap(), "Flash sale", idempotencyKey, now);
        // Giá flash sale chứ không phải giá niêm yết - vẫn là snapshot bất biến
        order.addItem(new OrderItem(variant, req.quantity(), item.getSalePrice()));
        order.recalculateTotals(FLAT_SHIPPING_FEE, BigDecimal.ZERO, null);
        order.logTransition(null, OrderStatus.PENDING_PAYMENT, userId, "Mua flash sale", now);

        Order saved;
        try {
            saved = orders.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            return orders.findByIdempotencyKey(idempotencyKey)
                    .map(OrderResponse::from)
                    .orElseThrow(() -> new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
        }

        outbox.recordOrderEvent(saved.getId(), OutboxRecorder.ORDER_PLACED, Map.of(
                "orderCode", saved.getCode(), "flashSaleItemId", itemId, "userId", userId));

        log.info("Flash sale: user {} mua {} suất của item {} -> đơn {}",
                userId, req.quantity(), itemId, saved.getCode());
        return OrderResponse.from(saved);
    }
}
