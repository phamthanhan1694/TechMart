package dev.techmart.audit;

import dev.techmart.audit.domain.AuditAction;
import dev.techmart.audit.domain.AuditLog;
import dev.techmart.audit.repository.AuditLogRepository;
import dev.techmart.catalog.domain.ProductVariant;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Audit log - ADR-0014: ghi cùng transaction, và không sửa/xoá được kể cả bằng SQL. */
@DisplayName("Audit log")
class AuditLogTest extends IntegrationTestBase {

    @Autowired AuditLogRepository auditLogs;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orders;
    @Autowired ReservationService reservationService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired TestDataFactory fixtures;
    @Autowired DatabaseCleaner cleaner;

    private OrderResponse order;
    private AuthPrincipal staff;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        ProductVariant variant = fixtures.createVariantWithStock(10, "5000000.00");
        User buyer = fixtures.createCustomerWithCart(variant.getId(), 1);
        var session = reservationService.hold(buyer.getId());
        order = orderService.placeOrder(buyer.getId(), new PlaceOrderRequest(session.sessionId(),
                new ShippingAddress("Nguyen Van A", "0901234567", "12 Nguyen Trai", "Ben Thanh", "Quan 1", "TP HCM"),
                null, null), UUID.randomUUID().toString());

        User staffUser = fixtures.createUser(Role.STAFF);
        staff = new AuthPrincipal(staffUser.getId(), staffUser.getEmail(), Role.STAFF);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(staff, null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Nhan vien doi trang thai don -> ghi ai, doi tu gi sang gi")
    void staffStatusChange_isAudited() {
        tx.executeWithoutResult(s -> orderService.transitionBySystem(
                orders.findByCode(order.code()).orElseThrow(), OrderStatus.PAID, "test"));
        orderService.updateStatus(order.code(), staff, new UpdateStatusRequest(OrderStatus.PACKED, "Da dong goi"));

        assertThat(auditLogs.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getAction()).isEqualTo(AuditAction.ORDER_STATUS_CHANGED);
            assertThat(log.getActorEmail()).isEqualTo(staff.email());
            assertThat(log.getActorRole()).isEqualTo("STAFF");
            assertThat(log.getTargetId()).isEqualTo(order.code());
            assertThat(log.getDetail())
                    .containsEntry("from", "PAID")
                    .containsEntry("to", "PACKED")
                    .containsEntry("note", "Da dong goi");
        });
    }

    @Test
    @DisplayName("Chuyen trang thai khong hop le -> rollback ca nhat ky, khong ghi dong nao")
    void failedAction_leavesNoAuditRow() {
        assertThatThrownBy(() -> orderService.updateStatus(order.code(), staff,
                new UpdateStatusRequest(OrderStatus.DELIVERED, null)))
                .isInstanceOf(RuntimeException.class);
        assertThat(auditLogs.count()).as("Nhat ky chi ton tai khi hanh dong da commit").isZero();
    }

    @Test
    @DisplayName("Trigger DB chan UPDATE va DELETE tren audit_logs")
    void auditRows_areImmutableInDatabase() {
        tx.executeWithoutResult(s -> orderService.transitionBySystem(
                orders.findByCode(order.code()).orElseThrow(), OrderStatus.PAID, "test"));
        orderService.updateStatus(order.code(), staff, new UpdateStatusRequest(OrderStatus.PACKED, null));
        AuditLog row = auditLogs.findAll().get(0);

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET actor_email = 'ke-xau@x' WHERE id = ?", row.getId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE id = ?", row.getId()))
                .hasMessageContaining("append-only");
        assertThat(auditLogs.count()).isEqualTo(1);
    }
}
