package dev.techmart.support;

import dev.techmart.cart.domain.Cart;
import dev.techmart.cart.repository.CartRepository;
import dev.techmart.catalog.domain.*;
import dev.techmart.catalog.repository.*;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.domain.User;
import dev.techmart.identity.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.techmart.order.domain.OrderStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Dựng dữ liệu cho test. Mỗi method tự mở transaction riêng để dùng được từ nhiều thread. */
@Component
public class TestDataFactory {

    private static final AtomicLong SEQ = new AtomicLong();

    private final UserRepository users;
    private final BrandRepository brands;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CartRepository carts;
    private final PasswordEncoder passwordEncoder;
    private final NamedParameterJdbcTemplate jdbc;

    public TestDataFactory(UserRepository users, BrandRepository brands, CategoryRepository categories,
                           ProductRepository products, ProductVariantRepository variants,
                           CartRepository carts, PasswordEncoder passwordEncoder,
                           NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.users = users;
        this.brands = brands;
        this.categories = categories;
        this.products = products;
        this.variants = variants;
        this.carts = carts;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User createUser(Role role) {
        long n = SEQ.incrementAndGet();
        return users.save(new User("user" + n + "@test.dev",
                passwordEncoder.encode("Password@123"), "Nguoi dung " + n, "0900000000", role));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User createCustomer() {
        return createUser(Role.CUSTOMER);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Brand createBrand() {
        long n = SEQ.incrementAndGet();
        return brands.save(new Brand("Brand " + n, "brand-" + n));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Category createCategory() {
        long n = SEQ.incrementAndGet();
        return categories.save(new Category("Danh muc " + n, "danh-muc-" + n, null));
    }

    /** Sản phẩm một phiên ban, tồn kho theo tham so. Dùng cho phần lớn test. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductVariant createVariantWithStock(int stock, String price) {
        long n = SEQ.incrementAndGet();
        Brand brand = brands.save(new Brand("Brand " + n, "brand-" + n));
        Category category = categories.save(new Category("Danh muc " + n, "danh-muc-" + n, null));

        Product product = new Product(category, brand, "San pham " + n, "san-pham-" + n,
                "Mo ta", "https://example.test/img.png",
                Map.of("ram", "8GB", "storage", "256GB"));
        product.addVariant(new ProductVariant("SKU-" + n, "Ban tieu chuan",
                new BigDecimal(price), null, stock, Map.of("color", "Den")));

        return products.save(product).getVariants().get(0);
    }

    /** Tạo user có sẵn giỏ hàng chưa {@code quantity} đơn vị của variant. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User createCustomerWithCart(Long variantId, int quantity) {
        User user = createUserInline();
        Cart cart = carts.save(new Cart(user));
        cart.addOrIncrease(variants.findById(variantId).orElseThrow(), quantity);
        carts.save(cart);
        return user;
    }

    private User createUserInline() {
        long n = SEQ.incrementAndGet();
        return users.save(new User("buyer" + n + "@test.dev",
                passwordEncoder.encode("Password@123"), "Khach " + n, "0900000000", Role.CUSTOMER));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductVariant reloadVariant(Long id) {
        return variants.findById(id).orElseThrow();
    }

    /**
     * Chèn thẳng một đơn hàng bằng SQL, đặt được {@code placed_at} và trạng thái tuỳ ý.
     *
     * <p>Dùng cho test báo cáo: cần đơn nằm đúng một thời điểm trong quá khứ và ở đúng một
     * trạng thái - đi qua OrderService thì placed_at luôn là "bây giờ". Không trừ tồn kho,
     * vì test báo cáo không đọc tồn kho của các đơn này.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long insertOrder(Long userId, Long variantId, OrderStatus status, Instant placedAt,
                            String total, String province, String couponCode, int quantity) {
        long n = SEQ.incrementAndGet();
        BigDecimal amount = new BigDecimal(total);
        Long orderId = jdbc.queryForObject("""
                INSERT INTO orders (code, user_id, status, subtotal, discount_amount, shipping_fee, total_amount,
                                    coupon_code, shipping_address, idempotency_key, placed_at)
                VALUES (:code, :userId, :status, :total, 0, 0, :total, :coupon,
                        CAST(:address AS jsonb), :key, :placedAt)
                RETURNING id
                """, new MapSqlParameterSource()
                .addValue("code", "TM-TEST-" + n)
                .addValue("userId", userId)
                .addValue("status", status.name())
                .addValue("total", amount)
                .addValue("coupon", couponCode)
                .addValue("address", "{\"province\":\"" + province + "\"}")
                .addValue("key", "test-" + n)
                .addValue("placedAt", Timestamp.from(placedAt)), Long.class);

        jdbc.update("""
                INSERT INTO order_items (order_id, variant_id, product_name, variant_name, sku,
                                         unit_price, quantity, line_total)
                VALUES (:orderId, :variantId, 'San pham', 'Ban test', 'SKU-T', :unit, :qty, :total)
                """, new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("variantId", variantId)
                .addValue("unit", amount.divide(BigDecimal.valueOf(quantity), 2, java.math.RoundingMode.HALF_UP))
                .addValue("qty", quantity)
                .addValue("total", amount));
        return orderId;
    }

    /** Ghi một bước chuyển trạng thái vào lịch sử - phễu đơn hàng đọc từ bảng này. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertHistory(long orderId, OrderStatus from, OrderStatus to, Instant at) {
        jdbc.update("""
                INSERT INTO order_status_history (order_id, from_status, to_status, created_at)
                VALUES (:orderId, :from, :to, :at)
                """, new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("from", from != null ? from.name() : null)
                .addValue("to", to.name())
                .addValue("at", Timestamp.from(at)));
    }

    /** Ghi đè created_at của user - để test "khách hàng mới theo ngày". */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setUserCreatedAt(Long userId, Instant createdAt) {
        jdbc.update("UPDATE users SET created_at = :at WHERE id = :id",
                Map.of("at", Timestamp.from(createdAt), "id", userId));
    }
}
