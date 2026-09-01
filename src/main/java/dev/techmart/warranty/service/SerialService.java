package dev.techmart.warranty.service;

import dev.techmart.catalog.repository.ProductVariantRepository;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.order.domain.Order;
import dev.techmart.order.domain.OrderItem;
import dev.techmart.warranty.domain.ProductSerial;
import dev.techmart.warranty.dto.WarrantyDtos.*;
import dev.techmart.warranty.repository.ProductSerialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Quan ly ton kho theo serial/IMEI.
 *
 * <p>Xem {@link ProductSerial} ve ly do ton tai song song voi {@code stock_quantity}.
 */
@Service
public class SerialService {

    private static final Logger log = LoggerFactory.getLogger(SerialService.class);
    private static final int DEFAULT_WARRANTY_MONTHS = 12;

    private final ProductSerialRepository serials;
    private final ProductVariantRepository variants;
    private final Clock clock;

    public SerialService(ProductSerialRepository serials, ProductVariantRepository variants, Clock clock) {
        this.serials = serials;
        this.variants = variants;
        this.clock = clock;
    }

    /** Tra cuu bao hanh - cong khai, khong can dang nhap, giong moi trang bao hanh that. */
    @Transactional(readOnly = true)
    public WarrantyLookupResponse lookup(String serialNo) {
        ProductSerial serial = serials.findBySerialNo(serialNo)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.SERIAL_NOT_FOUND, serialNo));
        return WarrantyLookupResponse.from(serial, clock.instant());
    }

    /** Nhap lo serial khi nhan hang tu nha cung cap. Serial trung se bi bo qua, khong lam hong ca lo. */
    @Transactional
    public BulkSerialResponse importBulk(BulkSerialRequest req) {
        var variant = variants.findById(req.variantId())
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.VARIANT_NOT_FOUND, req.variantId()));
        int months = req.warrantyMonths() != null ? req.warrantyMonths() : DEFAULT_WARRANTY_MONTHS;

        // Chuan hoa va bo trung ngay trong lo gui len
        Set<String> unique = new LinkedHashSet<>();
        req.serialNumbers().forEach(s -> unique.add(s.trim().toUpperCase()));

        List<String> duplicates = new ArrayList<>();
        List<ProductSerial> toSave = new ArrayList<>();
        for (String serialNo : unique) {
            if (serials.existsBySerialNoIgnoreCase(serialNo)) {
                duplicates.add(serialNo);
            } else {
                toSave.add(new ProductSerial(variant, serialNo, months));
            }
        }
        serials.saveAll(toSave);

        log.info("Da nhap {} serial cho variant {} ({} bi bo qua vi trung)",
                toSave.size(), req.variantId(), duplicates.size());
        return new BulkSerialResponse(toSave.size(), duplicates.size(), duplicates);
    }

    /**
     * Gan serial cho tung dong don hang khi <b>dong goi</b> (PAID -> PACKED).
     *
     * <p>Gan o buoc dong goi chu khong phai luc dat hang: gan luc dat hang thi moi lan mua
     * phai khoa va cap nhat N dong serial thay vi mot dong dem, dung cho dang tranh chap
     * nang nhat. Den buoc dong goi thi don da chot, khong con ai tranh nua.
     *
     * <p><b>Khong chan viec dong goi khi thieu serial.</b> Phan lon variant khong duoc theo doi
     * theo serial (khong co dong nao trong bang) - do la binh thuong. Thieu mot phan thi ghi
     * canh bao de kho xu ly, chu khong giu don hang lai: hang van phai di.
     */
    @Transactional
    public int assignForOrder(Order order) {
        Instant now = clock.instant();
        int assigned = 0;

        for (OrderItem item : order.getItems()) {
            Long variantId = item.getVariant().getId();
            if (serials.countByVariantIdAndStatus(variantId, dev.techmart.warranty.domain.SerialStatus.IN_STOCK) == 0) {
                continue;   // variant nay khong theo doi theo serial
            }
            List<ProductSerial> available =
                    serials.lockAvailableForVariant(variantId, Limit.of(item.getQuantity()));

            if (available.size() < item.getQuantity()) {
                log.warn("Don {} dong {}: can {} serial nhung chi con {} - kho can bo sung",
                        order.getCode(), item.getSku(), item.getQuantity(), available.size());
            }
            for (ProductSerial serial : available) {
                serial.assignTo(item, now);
                assigned++;
            }
        }
        if (assigned > 0) {
            log.info("Da gan {} serial cho don {}", assigned, order.getCode());
        }
        return assigned;
    }

    /** Hoan tra: serial ve trang thai RETURNED, cho kho kiem tra roi moi quyet dinh. */
    @Transactional
    public void releaseForOrder(Order order) {
        for (OrderItem item : order.getItems()) {
            serials.findByOrderItemId(item.getId()).forEach(ProductSerial::markReturned);
        }
    }

    @Transactional(readOnly = true)
    public List<SerialResponse> findByOrderItem(Long orderItemId) {
        return serials.findByOrderItemId(orderItemId).stream().map(SerialResponse::from).toList();
    }
}
