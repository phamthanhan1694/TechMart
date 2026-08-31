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

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Dung du lieu cho test. Moi method tu mo transaction rieng de dung duoc tu nhieu thread. */
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

    public TestDataFactory(UserRepository users, BrandRepository brands, CategoryRepository categories,
                           ProductRepository products, ProductVariantRepository variants,
                           CartRepository carts, PasswordEncoder passwordEncoder) {
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

    /** San pham mot phien ban, ton kho theo tham so. Dung cho phan lon test. */
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

    /** Tao user co san gio hang chua {@code quantity} don vi cua variant. */
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
}
