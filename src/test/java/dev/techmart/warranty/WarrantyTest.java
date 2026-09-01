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
 * Ton kho theo serial va bao hanh.
 *
 * <p>Diem dang noi: ban hang dua tren {@code stock_quantity} (nhanh, khoa mot dong),
 * con serial chi duoc gan khi DONG GOI. Neu gan luc dat hang thi moi lan mua phai khoa
 * N dong serial thay vi mot dong dem, dung cho dang tranh chap nang nhat.
 */
@DisplayName("Serial va bao hanh")
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
    @DisplayName("Nhap lo serial: bo qua serial trung, khong lam hong ca lo")
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
    @DisplayName("Serial duoc gan khi DONG GOI, khong phai luc dat hang")
    void serials_areAssignedAtPacking_notAtOrdering() {
        serialService.importBulk(new BulkSerialRequest(
                variant.getId(), List.of("IMEI-A", "IMEI-B", "IMEI-C"), 12));

        var order = placeOrder();
        assertThat(serials.countByVariantIdAndStatus(variant.getId(), SerialStatus.IN_STOCK))
                .as("Dat hang xong van chua gan serial nao")
                .isEqualTo(3);

        toPaid(order.code());
        orderService.updateStatus(order.code(), staff, new UpdateStatusRequest(OrderStatus.PACKED, null));

        assertThat(serials.countByVariantIdAndStatus(variant.getId(), SerialStatus.SOLD))
                .as("Dong goi 2 san pham thi gan dung 2 serial")
                .isEqualTo(2);
        assertThat(serials.countByVariantIdAndStatus(variant.getId(), SerialStatus.IN_STOCK)).isEqualTo(1);
    }

    @Test
    @DisplayName("Tra cuu bao hanh: chua ban thi bao chua ban, da ban thi tinh tu ngay xuat kho")
    void warrantyLookup_reflectsSaleState() {
        serialService.importBulk(new BulkSerialRequest(variant.getId(), List.of("IMEI-X", "IMEI-Y"), 12));

        var beforeSale = serialService.lookup("IMEI-X");
        assertThat(beforeSale.activatedAt()).isNull();
        assertThat(beforeSale.underWarranty()).isFalse();
        assertThat(beforeSale.message()).contains("chua duoc ban ra");

        var order = placeOrder();
        toPaid(order.code());
        orderService.updateStatus(order.code(), staff, new UpdateStatusRequest(OrderStatus.PACKED, null));

        var afterSale = serialService.lookup("IMEI-X");
        assertThat(afterSale.activatedAt()).as("Bao hanh tinh tu luc xuat kho").isNotNull();
        assertThat(afterSale.underWarranty()).isTrue();
        assertThat(afterSale.expiresAt()).isAfter(afterSale.activatedAt());
    }

    @Test
    @DisplayName("Serial khong ton tai -> 404")
    void warrantyLookup_unknownSerial_returns404() {
        assertThatThrownBy(() -> serialService.lookup("KHONG-TON-TAI"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.SERIAL_NOT_FOUND);
    }

    @Test
    @DisplayName("Yeu cau bao hanh cho serial cua nguoi khac -> 404")
    void warrantyRequest_forAnotherUsersSerial_returns404() {
        serialService.importBulk(new BulkSerialRequest(variant.getId(), List.of("IMEI-P", "IMEI-Q"), 12));
        var order = placeOrder();
        toPaid(order.code());
        orderService.updateStatus(order.code(), staff, new UpdateStatusRequest(OrderStatus.PACKED, null));

        User stranger = fixtures.createCustomer();
        var strangerPrincipal = new AuthPrincipal(stranger.getId(), stranger.getEmail(), Role.CUSTOMER);

        assertThatThrownBy(() -> warrantyService.create(strangerPrincipal,
                new CreateWarrantyRequest("IMEI-P", "May khong len nguon")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.SERIAL_NOT_FOUND);
    }

    @Test
    @DisplayName("Luong bao hanh: tao -> duyet -> hoan tat, va serial thanh DEFECTIVE")
    void warrantyFlow_approveThenComplete() {
        serialService.importBulk(new BulkSerialRequest(variant.getId(), List.of("IMEI-R", "IMEI-S"), 12));
        var order = placeOrder();
        toPaid(order.code());
        orderService.updateStatus(order.code(), staff, new UpdateStatusRequest(OrderStatus.PACKED, null));

        var request = warrantyService.create(principal,
                new CreateWarrantyRequest("IMEI-R", "Man hinh bi soc"));
        assertThat(request.status()).isEqualTo(WarrantyStatus.REQUESTED);

        // Mot serial chi co mot yeu cau dang mo
        assertThatThrownBy(() -> warrantyService.create(principal,
                new CreateWarrantyRequest("IMEI-R", "Them mot loi nua")))
                .isInstanceOf(BusinessException.class);

        var approved = warrantyService.resolve(staff, request.id(),
                new ResolveWarrantyRequest(WarrantyStatus.APPROVED, "Da nhan may, chuyen bo phan ky thuat"));
        assertThat(approved.status()).isEqualTo(WarrantyStatus.APPROVED);
        assertThat(serials.findBySerialNo("IMEI-R").orElseThrow().getStatus())
                .as("Duyet bao hanh nghia la may co loi that - khong duoc quay lai kho ban tiep")
                .isEqualTo(SerialStatus.DEFECTIVE);

        var completed = warrantyService.resolve(staff, request.id(),
                new ResolveWarrantyRequest(WarrantyStatus.COMPLETED, "Da thay man hinh, tra khach"));
        assertThat(completed.status()).isEqualTo(WarrantyStatus.COMPLETED);
        assertThat(completed.resolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("Chuyen trang thai bao hanh khong hop le -> 409")
    void warrantyFlow_invalidTransition_isRejected() {
        serialService.importBulk(new BulkSerialRequest(variant.getId(), List.of("IMEI-T", "IMEI-U"), 12));
        var order = placeOrder();
        toPaid(order.code());
        orderService.updateStatus(order.code(), staff, new UpdateStatusRequest(OrderStatus.PACKED, null));

        var request = warrantyService.create(principal, new CreateWarrantyRequest("IMEI-T", "Loi pin"));
        warrantyService.resolve(staff, request.id(),
                new ResolveWarrantyRequest(WarrantyStatus.REJECTED, "Loi do nguoi dung"));

        // REJECTED la trang thai dong, khong di tiep duoc
        assertThatThrownBy(() -> warrantyService.resolve(staff, request.id(),
                new ResolveWarrantyRequest(WarrantyStatus.COMPLETED, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("Variant khong theo doi serial thi dong goi van binh thuong")
    void packing_withoutSerialTracking_isNotBlocked() {
        var order = placeOrder();            // khong nhap serial nao
        toPaid(order.code());

        orderService.updateStatus(order.code(), staff, new UpdateStatusRequest(OrderStatus.PACKED, null));

        assertThat(orders.findByCode(order.code()).orElseThrow().getStatus())
                .as("Phan lon mat hang khong theo doi theo serial - khong duoc giu don lai vi the")
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
        return new ShippingAddress("Nguyen Van A", "0901234567", "12 Nguyen Trai",
                "Ben Thanh", "Quan 1", "TP Ho Chi Minh");
    }
}
