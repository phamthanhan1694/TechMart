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
 * kể cả ở trang danh sách với vô số biến thể khoá.
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
    @DisplayName("Đọc lần hai lấy từ cache, không bắn thêm truy vấn xuống DB")
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
    @DisplayName("Sửa giá -> lần đọc tiếp theo thấy giá MỚI, không phải giá cũ trong cache")
    void afterPriceChange_readReturnsNewValue() {
        ProductVariant variant = fixtures.createVariantWithStock(10, "20000000.00");
        String slug = variant.getProduct().getSlug();

        assertThat(productService.getBySlug(slug).variants().get(0).price())
                .isEqualByComparingTo("20000000.00");

        productService.updateVariant(variant.getId(), new VariantRequest(
                variant.getSku(), variant.getName(), new BigDecimal("31000000.00"),
                null, 10, Map.of("color", "Đen")));

        assertThat(productService.getBySlug(slug).variants().get(0).price())
                .as("Quen xoa cache o day la khach thay gia cu - loi khong bao gio lo ra trong test don le")
                .isEqualByComparingTo("31000000.00");
    }

    @Test
    @DisplayName("Bộ đếm thế hệ: một thay đổi làm VÔ HIỆU toàn bộ cache danh sách")
    void generationCounter_invalidatesAllListVariants() {
        ProductVariant variant = fixtures.createVariantWithStock(10, "20000000.00");

        // Nhiều biến thể khoá khác nhau của cùng một trang danh sách
        var byRam = criteria(Map.of("ram", "8GB"));
        var byStorage = criteria(Map.of("storage", "256GB"));
        assertThat(productService.search(byRam, PageRequest.of(0, 20)).totalElements()).isEqualTo(1);
        assertThat(productService.search(byStorage, PageRequest.of(0, 20)).totalElements()).isEqualTo(1);

        long generationBefore = generation();
        assertThat(redis.keys("catalog:list:" + generationBefore + ":*"))
                .as("Ca hai bien the khoa đều đã được cache")
                .hasSize(2);

        productService.softDelete(variant.getProduct().getId());

        assertThat(generation())
                .as("Mọi thay đổi catalog tăng thế hệ lên một")
                .isEqualTo(generationBefore + 1);
        assertThat(productService.search(byRam, PageRequest.of(0, 20)).totalElements())
                .as("Toàn bộ cache danh sách cũ tro thanh không thể chậm toi, không cần SCAN hay xoá hang loat")
                .isZero();
        assertThat(productService.search(byStorage, PageRequest.of(0, 20)).totalElements()).isZero();
    }

    @Test
    @DisplayName("Khoá cache gom đủ mọi tham số: bộ lọc khác nhau không dùng chung kết quả")
    void cacheKey_distinguishesDifferentQueries() {
        fixtures.createVariantWithStock(10, "20000000.00");

        var matching = productService.search(criteria(Map.of("ram", "8GB")), PageRequest.of(0, 20));
        var notMatching = productService.search(criteria(Map.of("ram", "64GB")), PageRequest.of(0, 20));

        assertThat(matching.totalElements()).isEqualTo(1);
        assertThat(notMatching.totalElements())
                .as("Bỏ sót tham số trong khoá cache là trả nhầm kết quả của truy vấn khác")
                .isZero();
    }

    @Test
    @DisplayName("Thứ tự key trong bộ lọc spec không tạo ra hai khoá cache khác nhau")
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
                .as("Cùng một truy vấn thì phải dùng chung một khoá, không nhân đôi cache")
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
