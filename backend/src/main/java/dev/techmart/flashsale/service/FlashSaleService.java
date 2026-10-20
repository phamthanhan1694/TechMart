package dev.techmart.flashsale.service;

import dev.techmart.audit.domain.AuditAction;
import dev.techmart.audit.service.AuditLogger;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.catalog.repository.ProductVariantRepository;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.common.observability.BusinessMetrics;
import dev.techmart.flashsale.domain.FlashSale;
import dev.techmart.flashsale.domain.FlashSaleItem;
import dev.techmart.flashsale.dto.FlashSaleDtos;
import dev.techmart.flashsale.dto.FlashSaleDtos.*;
import dev.techmart.flashsale.repository.FlashSaleItemRepository;
import dev.techmart.flashsale.repository.FlashSaleRepository;
import dev.techmart.order.dto.OrderDtos.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Mở bán giới hạn.
 *
 * <p>Kiến trúc hai tầng:
 * <ol>
 *   <li><b>Cửa Redis</b> - Lua script nguyên tử quyết định ai được mua. Chịu được vài nghìn
 *       request/giây trên cùng một suất bán.</li>
 *   <li><b>Sổ cái DB</b> - chỉ những người đã thắng ở cửa mới mở transaction ghi đơn hàng
 *       và trừ tồn kho thật. Số transaction bị chặn trên bởi {@code sale_quantity}.</li>
 * </ol>
 *
 * <p>Nếu bước 2 thất bại thì trả lại suất cho Redis - nếu không, suất đó bị "bốc hơi".
 */
@Service
@RequiredArgsConstructor
public class FlashSaleService {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleService.class);
    private static final BigDecimal FLAT_SHIPPING_FEE = new BigDecimal("30000.00");

    private final FlashSaleRepository sales;
    private final FlashSaleItemRepository items;
    private final FlashSaleStockCounter stockCounter;
    private final FlashSaleOrderWriter orderWriter;
    private final ProductVariantRepository variants;
    private final BusinessMetrics metrics;
    private final AuditLogger audit;
    private final Clock clock;

    // ------------------------------------------------------------------ đọc

    @Transactional(readOnly = true)
    public List<FlashSaleResponse> findActive() {
        Instant now = clock.instant();
        return sales.findActive(now).stream().map(s -> toResponse(s, now)).toList();
    }

    @Transactional(readOnly = true)
    public List<FlashSaleResponse> findUpcoming() {
        Instant now = clock.instant();
        return sales.findUpcoming(now).stream().map(s -> toResponse(s, now)).toList();
    }

    // ------------------------------------------------------------------ mua

    /**
     * Mua một suất flash sale.
     *
     * <p>Bước qua cửa Redis nằm NGOÀI transaction - cố ý. Nếu gọi Lua trong transaction thì
     * hàng nghìn request sẽ giữ connection DB trong lúc chờ Redis trả lời, và connection pool
     * cạn trước khi kịp từ chối ai.
     */
    public OrderResponse purchase(Long userId, Long itemId, PurchaseRequest req, String idempotencyKey) {
        FlashSaleItem item = items.findDetailById(itemId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.SOLD_OUT, itemId));

        Instant now = clock.instant();
        FlashSale sale = item.getFlashSale();
        if (now.isBefore(sale.getStartsAt())) {
            throw new BusinessException(ErrorCode.FLASH_SALE_NOT_STARTED);
        }
        if (sale.hasEndedAt(now)) {
            throw new BusinessException(ErrorCode.FLASH_SALE_ENDED);
        }

        long result = stockCounter.tryPurchase(itemId, userId, req.quantity(), item.getPerUserLimit());
        if (result == FlashSaleStockCounter.SOLD_OUT) {
            metrics.recordFlashSaleSoldOut();
            throw new BusinessException(ErrorCode.SOLD_OUT);
        }
        if (result == FlashSaleStockCounter.USER_LIMIT_EXCEEDED) {
            throw new BusinessException(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED,
                    "Mỗi người chỉ được mua tối đa " + item.getPerUserLimit() + " suất");
        }
        if (result == FlashSaleStockCounter.NOT_WARMED_UP) {
            // Fail-closed: không đảm bảo được thì từ chối bán, còn hơn bán quá số suất
            log.error("Bộ đếm Redis cho flash sale item {} không tồn tại - từ chối bán", itemId);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Phiên bán tạm thời không khả dụng");
        }

        try {
            return orderWriter.write(userId, itemId, req, idempotencyKey);
        } catch (RuntimeException e) {
            // Đã thắng ở cửa Redis nhưng ghi sổ cái thất bại -> trả lại suất
            stockCounter.refund(itemId, userId, req.quantity());
            log.error("Tạo đơn flash sale thất bại, đã trả lại {} suất cho item {}",
                    req.quantity(), itemId, e);
            throw e;
        }
    }

    // ------------------------------------------------------------------ ADMIN

    @Transactional
    public FlashSaleResponse create(CreateFlashSaleRequest req) {
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "endsAt phải sau startsAt");
        }
        FlashSale sale = new FlashSale(req.name(), req.startsAt(), req.endsAt());
        for (CreateItemRequest itemReq : req.items()) {
            ProductVariant variant = variants.findById(itemReq.variantId())
                    .orElseThrow(() -> BusinessException.notFound(ErrorCode.VARIANT_NOT_FOUND, itemReq.variantId()));
            if (variant.availableQuantity() < itemReq.saleQuantity()) {
                throw BusinessException.insufficientStock(Map.of(
                        "variantId", variant.getId(), "sku", variant.getSku(),
                        "requested", itemReq.saleQuantity(), "available", variant.availableQuantity()));
            }
            sale.addItem(new FlashSaleItem(variant, itemReq.salePrice(), itemReq.saleQuantity(),
                    itemReq.perUserLimit() != null ? itemReq.perUserLimit() : 1));
        }
        FlashSale saved = sales.save(sale);
        audit.record(AuditAction.FLASH_SALE_CREATED, "FLASH_SALE", saved.getId(), Map.of(
                "name", req.name(), "startsAt", req.startsAt().toString(), "items", req.items().size()));
        return toResponse(saved, clock.instant());
    }

    /**
     * Nạp sẵn tồn kho vào Redis trước giờ mở bán.
     * Không nạp trước thì đúng giây mở bán sẽ có hàng nghìn request cùng nạp - cache stampede.
     */
    @Transactional
    public int warmUp(Long saleId) {
        FlashSale sale = sales.findByIdWithItems(saleId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, saleId));
        sale.getItems().forEach(stockCounter::warmUp);
        sale.markRunning();
        return sale.getItems().size();
    }

    /**
     * Đối soát cuối phiên: số đã bán ở DB phải bằng số suất trừ tồn còn lại trên Redis.
     * Lệch thì log cảnh báo - đây là cái giá phải trả cho nhất quán cuối.
     */
    @Transactional(readOnly = true)
    public void reconcile(Long saleId) {
        for (FlashSaleItem item : items.findByFlashSaleId(saleId)) {
            int redisSold = stockCounter.remaining(item.getId())
                    .map(remaining -> item.getSaleQuantity() - remaining)
                    .orElse(item.getSoldQuantity());
            if (redisSold != item.getSoldQuantity()) {
                log.warn("Đối soát lệch ở flash sale item {}: Redis={} DB={}",
                        item.getId(), redisSold, item.getSoldQuantity());
            }
        }
    }

    private FlashSaleResponse toResponse(FlashSale sale, Instant now) {
        List<FlashSaleItemResponse> itemResponses = sale.getItems().stream()
                .map(item -> FlashSaleDtos.toItemResponse(item,
                        // Phiên đang chạy: đọc tồn từ Redis. Đã kết thúc: đọc từ DB.
                        stockCounter.remaining(item.getId()).orElseGet(item::remaining)))
                .toList();
        return FlashSaleDtos.toResponse(sale, itemResponses, now);
    }
}
