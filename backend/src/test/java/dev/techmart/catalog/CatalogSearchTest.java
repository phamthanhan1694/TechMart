package dev.techmart.catalog;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.catalog.dto.CatalogDtos.*;
import dev.techmart.catalog.repository.ProductSearchCriteria;
import dev.techmart.catalog.service.ProductService;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.web.PageableSanitizer;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.domain.User;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.inventory.service.ReservationService;
import dev.techmart.order.dto.OrderDtos.PlaceOrderRequest;
import dev.techmart.order.dto.OrderDtos.ShippingAddress;
import dev.techmart.order.service.OrderService;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import dev.techmart.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tìm kiếm trên JSONB (ADR-0002) và xoá mềm (ADR-0007).
 */
@DisplayName("Catalog")
class CatalogSearchTest extends IntegrationTestBase {

    @Autowired ProductService productService;
    @Autowired OrderService orderService;
    @Autowired ReservationService reservationService;
    @Autowired TestDataFactory fixtures;
    @Autowired DatabaseCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner.clean();
    }

    @Test
    @DisplayName("Lọc theo thông số kỹ thuật trên cột JSONB")
    void searchProducts_bySpecJsonb_returnsMatching() {
        // TestDataFactory tạo sản phẩm với spec {"ram":"8GB","storage":"256GB"}
        fixtures.createVariantWithStock(10, "20000000.00");
        fixtures.createVariantWithStock(10, "30000000.00");

        var matching = productService.search(
                criteria(Map.of("ram", "8GB")), PageRequest.of(0, 20));
        assertThat(matching.totalElements()).isEqualTo(2);

        var notMatching = productService.search(
                criteria(Map.of("ram", "64GB")), PageRequest.of(0, 20));
        assertThat(notMatching.totalElements())
                .as("Lọc theo giá trị không tồn tại phải trả về rỗng, không phải trả về tất cả")
                .isZero();
    }

    @Test
    @DisplayName("Lọc nhiều thông số cùng lúc phải thoả MỌI điều kiện")
    void searchProducts_multipleSpecFilters_areAndedTogether() {
        fixtures.createVariantWithStock(10, "20000000.00");

        assertThat(productService.search(
                criteria(Map.of("ram", "8GB", "storage", "256GB")), PageRequest.of(0, 20))
                .totalElements()).isEqualTo(1);

        assertThat(productService.search(
                criteria(Map.of("ram", "8GB", "storage", "1TB")), PageRequest.of(0, 20))
                .totalElements())
                .as("Một điều kiện sai thì không khớp, dù điều kiện kia đúng")
                .isZero();
    }

    @Test
    @DisplayName("size vượt giới hạn bị ép về 100")
    void pageSize_isCappedAt100() {
        var sanitized = PageableSanitizer.sanitize(PageRequest.of(0, 1_000_000));
        assertThat(sanitized.getPageSize())
                .as("Khong chan thi mot request size=1000000 du de ha service")
                .isEqualTo(PageableSanitizer.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("Xoá sản phẩm là xoá mềm và ẩn khỏi danh sách công khai")
    void deleteProduct_softDeletes_andHidesFromPublicList() {
        ProductVariant variant = fixtures.createVariantWithStock(10, "20000000.00");
        Long productId = variant.getProduct().getId();
        String slug = variant.getProduct().getSlug();

        assertThat(productService.search(criteria(Map.of()), PageRequest.of(0, 20)).totalElements())
                .isEqualTo(1);

        productService.softDelete(productId);

        assertThat(productService.search(criteria(Map.of()), PageRequest.of(0, 20)).totalElements())
                .as("Sản phẩm đã xoá mềm không được hiện ở danh sách công khai")
                .isZero();
        assertThatThrownBy(() -> productService.getBySlug(slug))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Xoá sản phẩm đã nằm trong đơn hàng KHÔNG làm hỏng lịch sử đơn")
    void deleteProduct_alreadyInOrder_doesNotBreakOrderHistory() {
        ProductVariant variant = fixtures.createVariantWithStock(10, "20000000.00");
        User buyer = fixtures.createCustomerWithCart(variant.getId(), 2);

        var session = reservationService.hold(buyer.getId());
        var order = orderService.placeOrder(buyer.getId(),
                new PlaceOrderRequest(session.sessionId(), address(), null, null),
                UUID.randomUUID().toString());

        String productName = order.items().get(0).productName();
        BigDecimal unitPrice = order.items().get(0).unitPrice();

        // Đây là chỗ mà xoá cứng sẽ vi phạm khoá ngoại với order_items
        productService.softDelete(variant.getProduct().getId());

        var principal = new AuthPrincipal(buyer.getId(), buyer.getEmail(), Role.CUSTOMER);
        var reloaded = orderService.getByCode(order.code(), principal);

        assertThat(reloaded.items()).hasSize(1);
        assertThat(reloaded.items().get(0).productName())
                .as("Đơn cũ đọc được nho snapshot, không join ngược sang products")
                .isEqualTo(productName);
        assertThat(reloaded.items().get(0).unitPrice()).isEqualByComparingTo(unitPrice);
        assertThat(reloaded.totalAmount()).isEqualByComparingTo(order.totalAmount());
    }

    @Test
    @DisplayName("Đổi giá sản phẩm KHÔNG làm đổi giá của đơn hàng cũ")
    void changingPrice_doesNotAffectExistingOrders() {
        ProductVariant variant = fixtures.createVariantWithStock(10, "20000000.00");
        User buyer = fixtures.createCustomerWithCart(variant.getId(), 1);

        var session = reservationService.hold(buyer.getId());
        var order = orderService.placeOrder(buyer.getId(),
                new PlaceOrderRequest(session.sessionId(), address(), null, null),
                UUID.randomUUID().toString());
        BigDecimal pricePaid = order.items().get(0).unitPrice();

        productService.updateVariant(variant.getId(), new VariantRequest(
                variant.getSku(), variant.getName(), new BigDecimal("99000000.00"),
                null, 10, Map.of("color", "Đen")));

        var principal = new AuthPrincipal(buyer.getId(), buyer.getEmail(), Role.CUSTOMER);
        assertThat(orderService.getByCode(order.code(), principal).items().get(0).unitPrice())
                .as("Đơn hàng là bản ghi lịch sử bất biến")
                .isEqualByComparingTo(pricePaid);
    }

    @Test
    @DisplayName("Đặt hàng hai lần cùng Idempotency-Key -> chỉ một đơn")
    void placeOrder_sameIdempotencyKeyTwice_createsOneOrder() {
        ProductVariant variant = fixtures.createVariantWithStock(10, "20000000.00");
        User buyer = fixtures.createCustomerWithCart(variant.getId(), 1);

        var session = reservationService.hold(buyer.getId());
        String key = UUID.randomUUID().toString();
        var req = new PlaceOrderRequest(session.sessionId(), address(), null, null);

        var first = orderService.placeOrder(buyer.getId(), req, key);
        var second = orderService.placeOrder(buyer.getId(), req, key);

        assertThat(second.code()).isEqualTo(first.code());
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(fixtures.reloadVariant(variant.getId()).getStockQuantity())
                .as("Chi trừ tồn kho một lần")
                .isEqualTo(9);
    }

    private static ProductSearchCriteria criteria(Map<String, String> spec) {
        return new ProductSearchCriteria(null, null, null, null, null, spec, null);
    }

    private static ShippingAddress address() {
        return new ShippingAddress("Nguyễn Văn A", "0901234567", "12 Nguyen Trai",
                "Ben Thanh", "Quận 1", "TP Hồ Chí Minh");
    }
}
