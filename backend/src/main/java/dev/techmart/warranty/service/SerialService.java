package dev.techmart.warranty.service;

import dev.techmart.catalog.repository.ProductVariantRepository;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.order.domain.Order;
import dev.techmart.order.domain.OrderItem;
import dev.techmart.warranty.domain.ProductSerial;
import dev.techmart.warranty.dto.WarrantyDtos.*;
import dev.techmart.warranty.repository.ProductSerialRepository;
import lombok.RequiredArgsConstructor;
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
 * Quản lý tồn kho theo serial/IMEI.
 *
 * <p>Xem {@link ProductSerial} về lý do tồn tại song song với {@code stock_quantity}.
 */
@Service
@RequiredArgsConstructor
public class SerialService {

    private static final Logger log = LoggerFactory.getLogger(SerialService.class);
    private static final int DEFAULT_WARRANTY_MONTHS = 12;

    private final ProductSerialRepository serials;
    private final ProductVariantRepository variants;
    private final Clock clock;

    /** Tra cứu bảo hành - công khai, không cần đăng nhập, giống mọi trang bảo hành thật. */
    @Transactional(readOnly = true)
    public WarrantyLookupResponse lookup(String serialNo) {
        ProductSerial serial = serials.findBySerialNo(serialNo)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.SERIAL_NOT_FOUND, serialNo));
        return WarrantyLookupResponse.from(serial, clock.instant());
    }

    /** Nhập lô serial khi nhận hàng từ nhà cung cấp. Serial trùng sẽ bị bỏ qua, không làm hỏng cả lô. */
    @Transactional
    public BulkSerialResponse importBulk(BulkSerialRequest req) {
        var variant = variants.findById(req.variantId())
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.VARIANT_NOT_FOUND, req.variantId()));
        int months = req.warrantyMonths() != null ? req.warrantyMonths() : DEFAULT_WARRANTY_MONTHS;

        // Chuẩn hoá và bỏ trùng ngay trong lô gửi lên
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

        log.info("Đã nhập {} serial cho variant {} ({} bị bỏ qua vì trùng)",
                toSave.size(), req.variantId(), duplicates.size());
        return new BulkSerialResponse(toSave.size(), duplicates.size(), duplicates);
    }

    /**
     * Gán serial cho từng dòng đơn hàng khi <b>đóng gói</b> (PAID -> PACKED).
     *
     * <p>Gán ở bước đóng gói chứ không phải lúc đặt hàng: gắn lúc đặt hàng thì mỗi lần mua
     * phải khoá và cập nhật N dòng serial thay vì một dòng đếm, đúng chỗ đang tranh chấp
     * nặng nhất. Đến bước đóng gói thì đơn đã chốt, không còn ai tranh nữa.
     *
     * <p><b>Không chặn việc đóng gói khi thiếu serial.</b> Phần lớn variant không được theo dõi
     * theo serial (không có dòng nào trong bảng) - đó là bình thường. Thiếu một phần thì ghi
     * cảnh báo để kho xử lý, chứ không giữ đơn hàng lại: hàng vẫn phải đi.
     */
    @Transactional
    public int assignForOrder(Order order) {
        Instant now = clock.instant();
        int assigned = 0;

        for (OrderItem item : order.getItems()) {
            Long variantId = item.getVariant().getId();
            if (serials.countByVariantIdAndStatus(variantId, dev.techmart.warranty.domain.SerialStatus.IN_STOCK) == 0) {
                continue;   // variant này không theo dõi theo serial
            }
            List<ProductSerial> available =
                    serials.lockAvailableForVariant(variantId, Limit.of(item.getQuantity()));

            if (available.size() < item.getQuantity()) {
                log.warn("Đơn {} dòng {}: cần {} serial nhưng chỉ còn {} - kho cần bổ sung",
                        order.getCode(), item.getSku(), item.getQuantity(), available.size());
            }
            for (ProductSerial serial : available) {
                serial.assignTo(item, now);
                assigned++;
            }
        }
        if (assigned > 0) {
            log.info("Đã gán {} serial cho đơn {}", assigned, order.getCode());
        }
        return assigned;
    }

    /** Hoàn trả: serial về trạng thái RETURNED, chờ kho kiểm tra rồi mới quyết định. */
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
