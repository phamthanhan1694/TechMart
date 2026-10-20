package dev.techmart.warranty;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.domain.User;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.inventory.service.ReservationService;
import dev.techmart.order.domain.OrderStatus;
import dev.techmart.order.dto.OrderDtos.*;
import dev.techmart.order.repository.OrderRepository;
import dev.techmart.order.service.OrderService;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import dev.techmart.support.TestDataFactory;
import dev.techmart.warranty.domain.SerialStatus;
import dev.techmart.warranty.domain.WarrantyStatus;
import dev.techmart.warranty.dto.WarrantyDtos.*;
import dev.techmart.warranty.repository.ProductSerialRepository;
import dev.techmart.warranty.service.SerialService;
import dev.techmart.warranty.service.WarrantyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tồn kho theo serial và bảo hành.
 *
 * <p>Điểm đáng nói: bán hàng dựa trên {@code stock_quantity} (nhanh, khoá một dòng),
 * còn serial chỉ được gắn khi ĐÓNG GÓI. Nếu gắn lúc đặt hàng thì mỗi lần mua phải khoá
 * N dòng serial thay vì một dòng đếm, đúng chỗ đang tranh chấp nặng nhất.
 */
@DisplayName("Serial và bảo hành")
class WarrantyTest extends IntegrationTestBase {

    @Autowired SerialService serialService;
    @Autowired WarrantyService warrantyService;
    @Autowired ProductSerialRepository serials;
    @Autowired OrderService orderService;
    @Autowired ReservationService reservationService;
    @Autowired OrderRepository orders;
    @Autowired TransactionTemplate txTemplate;
    @Autowired TestDataFactory fixtures;
    @Autowired DatabaseCleaner cleaner;

    private ProductVariant variant;
    private User buyer;
    private AuthPrincipal principal;
    private AuthPrincipal staff;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        variant = fixtures.createVariantWithStock(20, "20000000.00");
        buyer = fixtures.createCustomerWithCart(variant.getId(), 2);
        principal = new AuthPrincipal(buyer.getId(), buyer.getEmail(), Role.CUSTOMER);
        staff = new AuthPrincipal(fixtures.createUser(Role.STAFF).getId(), "staff@test.dev", Role.STAFF);
    }

    @Test
    @DisplayName("Nhập lô serial: bỏ qua serial trùng, không làm hỏng cả lô")
    void importBulk_skipsDuplicates() {
        var first = serialService.importBulk(new BulkSerialRequest(
                variant.getId(), List.of("IMEI-001", "IMEI-002"), 24));
        assertThat(first.imported()).isEqualTo(2);

        var second = serialService.importBulk(new BulkSerialRequest(
                variant.getId(), List.of("IMEI-002", "IMEI-003"), 24));
        assertThat(second.imported()).isEqualTo(1);
        assertThat(second.skippedDuplicates()).isEqualTo(1);
        assertThat(second.duplicates()).containsExactly("IMEI-002");
    }

    @Test
    @DisplayName("Serial được gán khi ĐÓNG GÓI, không phải lúc đặt hàng")
    void serials_areAssignedAtPacking_notAtOrdering() {
        serialService.importBulk(new BulkSerialRequest(
                variant.getId(), List.of("IMEI-A", "IMEI-B", "IMEI-C"), 12));

        var order = placeOrder();
        assertThat(serials.countByVariantIdAndStatus(variant.getId(), SerialStatus.IN_STOCK))
                .as("Đặt hàng xong vẫn chưa gan serial nào")
                .isEqualTo(3);

        toPaid(order.code());
        orderService.updateStatus(order.code(), staff, new UpdateStatusRequest(OrderStatus.PACKED, null));

        assertThat(serials.countByVariantIdAndStatus(variant.getId(), SerialStatus.SOLD))
                .as("Đóng gói 2 sản phẩm thì gán đúng 2 serial")
                .isEqualTo(2);
        assertThat(serials.countByVariantIdAndStatus(variant.getId(), SerialStatus.IN_STOCK)).isEqualTo(1);
    }

    @Test
    @DisplayName("Tra cứu bảo hành: chưa bán thì báo chưa bán, đã bán thì tính từ ngày xuất kho")
    void warrantyLookup_reflectsSaleState() {
        serialService.importBulk(new BulkSerialRequest(variant.getId(), List.of("IMEI-X", "IMEI-Y"), 12));

        var beforeSale = serialService.lookup("IMEI-X");
        assertThat(beforeSale.activatedAt()).isNull();
        assertThat(beforeSale.underWarranty()).isFalse();
        assertThat(beforeSale.message()).contains("chưa được bán ra");

        var order = placeOrder();
        toPaid(order.code());
        orderService.updateStatus(order.code(), staff, new UpdateStatusRequest(OrderStatus.PACKED, null));

        var afterSale = serialService.lookup("IMEI-X");
        assertThat(afterSale.activatedAt()).as("Bảo hành tính từ lúc xuất kho").isNotNull();
        assertThat(afterSale.underWarranty()).isTrue();
        assertThat(afterSale.expiresAt()).isAfter(afterSale.activatedAt());
    }

    @Test
    @DisplayName("Serial không tồn tại -> 404")
    void warrantyLookup_unknownSerial_returns404() {
        assertThatThrownBy(() -> serialService.lookup("KHONG-TON-TAI"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.SERIAL_NOT_FOUND);
    }

    @Test
    @DisplayName("Yêu cầu bảo hành cho serial của người khác -> 404")
    void warrantyRequest_forAnotherUsersSerial_returns404() {
        serialService.importBulk(new BulkSerialRequest(variant.getId(), List.of("IMEI-P", "IMEI-Q"), 12));
        var order = placeOrder();
        toPaid(order.code());
        orderService.updateStatus(order.code(), staff, new UpdateStatusRequest(OrderStatus.PACKED, null));

        User stranger = fixtures.createCustomer();
        var strangerPrincipal = new AuthPrincipal(stranger.getId(), stranger.getEmail(), Role.CUSTOMER);

        assertThatThrownBy(() -> warrantyService.create(strangerPrincipal,
                new CreateWarrantyRequest("IMEI-P", "Máy không lên nguồn")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.SERIAL_NOT_FOUND);
    }

    @Test
    @DisplayName("Luồng bảo hành: tạo -> duyệt -> hoàn tất, và serial thành DEFECTIVE")
    void warrantyFlow_approveThenComplete() {
        serialService.importBulk(new BulkSerialRequest(variant.getId(), List.of("IMEI-R", "IMEI-S"), 12));
        var order = placeOrder();
        toPaid(order.code());
        orderService.updateStatus(order.code(), staff, new UpdateStatusRequest(OrderStatus.PACKED, null));

        var request = warrantyService.create(principal,
                new CreateWarrantyRequest("IMEI-R", "Màn hình bị sọc"));
        assertThat(request.status()).isEqualTo(WarrantyStatus.REQUESTED);

        // Một serial chỉ có một yêu cầu đang mở
        assertThatThrownBy(() -> warrantyService.create(principal,
                new CreateWarrantyRequest("IMEI-R", "Thêm một lỗi nữa")))
                .isInstanceOf(BusinessException.class);

        var approved = warrantyService.resolve(staff, request.id(),
                new ResolveWarrantyRequest(WarrantyStatus.APPROVED, "Đã nhận máy, chuyển bộ phận kỹ thuật"));
        assertThat(approved.status()).isEqualTo(WarrantyStatus.APPROVED);
        assertThat(serials.findBySerialNo("IMEI-R").orElseThrow().getStatus())
                .as("Duyệt bảo hành nghĩa là máy có lỗi thật - không được quay lại kho bán tiếp")
                .isEqualTo(SerialStatus.DEFECTIVE);

        var completed = warrantyService.resolve(staff, request.id(),
                new ResolveWarrantyRequest(WarrantyStatus.COMPLETED, "Đã thay màn hình, trả khách"));
        assertThat(completed.status()).isEqualTo(WarrantyStatus.COMPLETED);
        assertThat(completed.resolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("Chuyển trạng thái bảo hành không hợp lệ -> 409")
    void warrantyFlow_invalidTransition_isRejected() {
        serialService.importBulk(new BulkSerialRequest(variant.getId(), List.of("IMEI-T", "IMEI-U"), 12));
        var order = placeOrder();
        toPaid(order.code());
        orderService.updateStatus(order.code(), staff, new UpdateStatusRequest(OrderStatus.PACKED, null));

        var request = warrantyService.create(principal, new CreateWarrantyRequest("IMEI-T", "Lỗi pin"));
        warrantyService.resolve(staff, request.id(),
                new ResolveWarrantyRequest(WarrantyStatus.REJECTED, "Loi do người dùng"));

        // REJECTED là trạng thái dừng, không đi tiếp được
        assertThatThrownBy(() -> warrantyService.resolve(staff, request.id(),
                new ResolveWarrantyRequest(WarrantyStatus.COMPLETED, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("Variant không theo dõi serial thì đóng gói vẫn bình thường")
    void packing_withoutSerialTracking_isNotBlocked() {
        var order = placeOrder();            // không nhập serial nào
        toPaid(order.code());

        orderService.updateStatus(order.code(), staff, new UpdateStatusRequest(OrderStatus.PACKED, null));

        assertThat(orders.findByCode(order.code()).orElseThrow().getStatus())
                .as("Phần lớn mất hang không theo doi theo serial - không được giữ đơn lại vi the")
                .isEqualTo(OrderStatus.PACKED);
    }

    private OrderResponse placeOrder() {
        var session = reservationService.hold(buyer.getId());
        return orderService.placeOrder(buyer.getId(),
                new PlaceOrderRequest(session.sessionId(), address(), null, null),
                UUID.randomUUID().toString());
    }

    private void toPaid(String code) {
        txTemplate.executeWithoutResult(status -> orderService.transitionBySystem(
                orders.findByCode(code).orElseThrow(), OrderStatus.PAID, "test"));
    }

    private static ShippingAddress address() {
        return new ShippingAddress("Nguyễn Văn A", "0901234567", "12 Nguyen Trai",
                "Ben Thanh", "Quận 1", "TP Hồ Chí Minh");
    }
}
