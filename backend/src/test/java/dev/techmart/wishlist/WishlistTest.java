package dev.techmart.wishlist;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.catalog.service.ProductService;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.domain.User;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import dev.techmart.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Danh sach yeu thich")
class WishlistTest extends IntegrationTestBase {

    @Autowired WishlistService wishlist;
    @Autowired ProductService productService;
    @Autowired TestDataFactory fixtures;
    @Autowired DatabaseCleaner cleaner;

    private User user;
    private Long productId;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        user = fixtures.createCustomer();
        ProductVariant variant = fixtures.createVariantWithStock(5, "15000000.00");
        productId = variant.getProduct().getId();
    }

    @Test
    @DisplayName("Them hai lan = mot dong (PUT idempotent, khong 409)")
    void add_isIdempotent() {
        wishlist.add(user.getId(), productId);
        wishlist.add(user.getId(), productId);

        assertThat(wishlist.list(user.getId())).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(productId);
            assertThat(item.minPrice()).isEqualByComparingTo("15000000");
            assertThat(item.availableQuantity()).isEqualTo(5);
            assertThat(item.purchasable()).isTrue();
        });
        assertThat(wishlist.productIds(user.getId())).containsExactly(productId);
    }

    @Test
    @DisplayName("Xoa thu khong co cung thanh cong; danh sach moi nguoi tach biet")
    void remove_isIdempotent_andListsArePrivate() {
        User other = fixtures.createCustomer();
        wishlist.add(user.getId(), productId);

        wishlist.remove(other.getId(), productId);   // người khác xoá -> không ảnh hưởng
        assertThat(wishlist.list(user.getId())).hasSize(1);
        assertThat(wishlist.list(other.getId())).isEmpty();

        wishlist.remove(user.getId(), productId);
        wishlist.remove(user.getId(), productId);
        assertThat(wishlist.list(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("San pham ngung ban: khong them moi duoc, nhung van nam trong danh sach da co")
    void discontinuedProduct() {
        wishlist.add(user.getId(), productId);
        productService.softDelete(productId);

        assertThat(wishlist.list(user.getId())).singleElement()
                .satisfies(item -> assertThat(item.purchasable()).isFalse());

        User other = fixtures.createCustomer();
        assertThatThrownBy(() -> wishlist.add(other.getId(), productId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }
}
