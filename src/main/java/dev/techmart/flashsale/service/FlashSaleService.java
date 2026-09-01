package dev.techmart.flashsale.service;

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
 * Mo ban gioi han.
 *
 * <p>Kien truc hai tang:
 * <ol>
 *   <li><b>Cua Redis</b> - Lua script nguyen tu quyet dinh ai duoc mua. Chiu duoc vai nghin
 *       request/giay tren cung mot suat ban.</li>
 *   <li><b>So cai DB</b> - chi nhung nguoi da thang o cua moi mo transaction ghi don hang
 *       va tru ton kho that. So transaction bi chan tren boi {@code sale_quantity}.</li>
 * </ol>
 *
 * <p>Neu buoc 2 that bai thi tra lai suat cho Redis - neu khong, suat do bi "boc hoi".
 */
@Service
public class FlashSaleService {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleService.class);
    private static final BigDecimal FLAT_SHIPPING_FEE = new BigDecimal("30000.00");

    private final FlashSaleRepository sales;
    private final FlashSaleItemRepository items;
    private final FlashSaleStockCounter stockCounter;
    private final FlashSaleOrderWriter orderWriter;
    private final ProductVariantRepository variants;
    private final BusinessMetrics metrics;
    private final Clock clock;

    public FlashSaleService(FlashSaleRepository sales, FlashSaleItemRepository items,
                            FlashSaleStockCounter stockCounter, FlashSaleOrderWriter orderWriter,
                            ProductVariantRepository variants, BusinessMetrics metrics, Clock clock) {
        this.sales = sales;
        this.items = items;
        this.stockCounter = stockCounter;
        this.orderWriter = orderWriter;
        this.variants = variants;
        this.metrics = metrics;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ doc

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
     * Mua mot suat flash sale.
     *
     * <p>Buoc qua cua Redis nam NGOAI transaction - co y. Neu goi Lua trong transaction thi
     * hang nghin request se giu connection DB trong luc cho Redis tra loi, va connection pool
     * can truoc khi kip tu choi ai.
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
                    "Moi nguoi chi duoc mua toi da " + item.getPerUserLimit() + " suat");
        }
        if (result == FlashSaleStockCounter.NOT_WARMED_UP) {
            // Fail-closed: khong dam bao duoc thi tu choi ban, con hon ban qua so suat
            log.error("Bo dem Redis cho flash sale item {} khong ton tai - tu choi ban", itemId);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Phien ban tam thoi khong kha dung");
        }

        try {
            return orderWriter.write(userId, itemId, req, idempotencyKey);
        } catch (RuntimeException e) {
            // Da thang o cua Redis nhung ghi so cai that bai -> tra lai suat
            stockCounter.refund(itemId, userId, req.quantity());
            log.error("Tao don flash sale that bai, da tra lai {} suat cho item {}",
                    req.quantity(), itemId, e);
            throw e;
        }
    }

    // ------------------------------------------------------------------ ADMIN

    @Transactional
    public FlashSaleResponse create(CreateFlashSaleRequest req) {
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "endsAt phai sau startsAt");
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
        return toResponse(saved, clock.instant());
    }

    /**
     * Nap san ton kho vao Redis truoc gio mo ban.
     * Khong nap truoc thi dung giay mo ban se co hang nghin request cung nap - cache stampede.
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
     * Doi soat cuoi phien: so da ban o DB phai bang so suat tru ton con lai tren Redis.
     * Lech thi log canh bao - day la cai gia phai tra cho nhat quan cuoi.
     */
    @Transactional(readOnly = true)
    public void reconcile(Long saleId) {
        for (FlashSaleItem item : items.findByFlashSaleId(saleId)) {
            int redisSold = stockCounter.remaining(item.getId())
                    .map(remaining -> item.getSaleQuantity() - remaining)
                    .orElse(item.getSoldQuantity());
            if (redisSold != item.getSoldQuantity()) {
                log.warn("Doi soat lech o flash sale item {}: Redis={} DB={}",
                        item.getId(), redisSold, item.getSoldQuantity());
            }
        }
    }

    private FlashSaleResponse toResponse(FlashSale sale, Instant now) {
        List<FlashSaleItemResponse> itemResponses = sale.getItems().stream()
                .map(item -> FlashSaleDtos.toItemResponse(item,
                        // Phien dang chay: doc ton tu Redis. Da ket thuc: doc tu DB.
                        stockCounter.remaining(item.getId()).orElseGet(item::remaining)))
                .toList();
        return FlashSaleDtos.toResponse(sale, itemResponses, now);
    }
}
