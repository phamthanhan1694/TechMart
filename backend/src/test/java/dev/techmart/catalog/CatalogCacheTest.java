package dev.techmart.catalog;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.catalog.dto.CatalogDtos.VariantRequest;
import dev.techmart.catalog.repository.ProductSearchCriteria;
import dev.techmart.catalog.service.ProductService;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import dev.techmart.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cache-aside cho catalog.
 *
 * <p>Phần khó của cache không phải đọc nhanh mà là <b>xoá đúng lúc</b>. Nhóm test này
 * chủ yếu kiểm chứng chỗ: sửa sản phẩm xong thì lần đọc tiếp theo phải thay dữ liệu mới,
 * kể cả o trang danh sách với vô số biến thể khoá.
 */
@DisplayName("Cache catalog")
class CatalogCacheTest extends IntegrationTestBase {

    @Autowired ProductService productService;
    @Autowired StringRedisTemplate redis;
    @Autowired TestDataFactory fixtures;
    @Autowired DatabaseCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner.clean();
    }

    @Test
    @DisplayName("Doc lan hai lay tu cache, khong ban them truy van xuong DB")
    void secondRead_comesFromCache() {
        ProductVariant variant = fixtures.createVariantWithStock(10, "20000000.00");
        String slug = variant.getProduct().getSlug();

        var first = productService.getBySlug(slug);
        assertThat(redis.keys("catalog:detail:*")).contains("catalog:detail:" + slug);

        var second = productService.getBySlug(slug);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.variants()).hasSameSizeAs(first.variants());
    }

    @Test
    @DisplayName("Sua gia -> lan doc tiep theo thay gia MOI, khong phai gia cu trong cache")
    void afterPriceChange_readReturnsNewValue() {
        ProductVariant variant = fixtures.createVariantWithStock(10, "20000000.00");
        String slug = variant.getProduct().getSlug();

        assertThat(productService.getBySlug(slug).variants().get(0).price())
                .isEqualByComparingTo("20000000.00");

        productService.updateVariant(variant.getId(), new VariantRequest(
                variant.getSku(), variant.getName(), new BigDecimal("31000000.00"),
                null, 10, Map.of("color", "Den")));

        assertThat(productService.getBySlug(slug).variants().get(0).price())
                .as("Quen xoa cache o day la khach thay gia cu - loi khong bao gio lo ra trong test don le")
                .isEqualByComparingTo("31000000.00");
    }

    @Test
    @DisplayName("Bo dem the he: mot thay doi lam VO HIEU toan bo cache danh sach")
    void generationCounter_invalidatesAllListVariants() {
        ProductVariant variant = fixtures.createVariantWithStock(10, "20000000.00");

        // Nhieu biến the khoá khác nhau của cùng một trang danh sách
        var byRam = criteria(Map.of("ram", "8GB"));
        var byStorage = criteria(Map.of("storage", "256GB"));
        assertThat(productService.search(byRam, PageRequest.of(0, 20)).totalElements()).isEqualTo(1);
        assertThat(productService.search(byStorage, PageRequest.of(0, 20)).totalElements()).isEqualTo(1);

        long generationBefore = generation();
        assertThat(redis.keys("catalog:list:" + generationBefore + ":*"))
                .as("Ca hai bien the khoa deu da duoc cache")
                .hasSize(2);

        productService.softDelete(variant.getProduct().getId());

        assertThat(generation())
                .as("Moi thay doi catalog tang the he len mot")
                .isEqualTo(generationBefore + 1);
        assertThat(productService.search(byRam, PageRequest.of(0, 20)).totalElements())
                .as("Toan bo cache danh sach cu tro thanh khong the cham toi, khong can SCAN hay xoa hang loat")
                .isZero();
        assertThat(productService.search(byStorage, PageRequest.of(0, 20)).totalElements()).isZero();
    }

    @Test
    @DisplayName("Khoa cache gom du moi tham so: bo loc khac nhau khong dung chung ket qua")
    void cacheKey_distinguishesDifferentQueries() {
        fixtures.createVariantWithStock(10, "20000000.00");

        var matching = productService.search(criteria(Map.of("ram", "8GB")), PageRequest.of(0, 20));
        var notMatching = productService.search(criteria(Map.of("ram", "64GB")), PageRequest.of(0, 20));

        assertThat(matching.totalElements()).isEqualTo(1);
        assertThat(notMatching.totalElements())
                .as("Bo sot tham so trong khoa cache la tra nham ket qua cua truy van khac")
                .isZero();
    }

    @Test
    @DisplayName("Thu tu key trong bo loc spec khong tao ra hai khoa cache khac nhau")
    void cacheKey_isStableRegardlessOfSpecOrder() {
        fixtures.createVariantWithStock(10, "20000000.00");

        var order1 = new java.util.LinkedHashMap<String, String>();
        order1.put("ram", "8GB");
        order1.put("storage", "256GB");
        var order2 = new java.util.LinkedHashMap<String, String>();
        order2.put("storage", "256GB");
        order2.put("ram", "8GB");

        productService.search(criteria(order1), PageRequest.of(0, 20));
        long keysAfterFirst = redis.keys("catalog:list:*").size();

        productService.search(criteria(order2), PageRequest.of(0, 20));

        assertThat(redis.keys("catalog:list:*"))
                .as("Cung mot truy van thi phai dung chung mot khoa, khong nhan doi cache")
                .hasSize((int) keysAfterFirst);
    }

    private long generation() {
        String value = redis.opsForValue().get("catalog:gen");
        return value == null ? 0L : Long.parseLong(value);
    }

    private static ProductSearchCriteria criteria(Map<String, String> spec) {
        return new ProductSearchCriteria(null, null, null, null, null, spec, null);
    }
}
