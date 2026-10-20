package dev.techmart.catalog.service;

import dev.techmart.audit.domain.AuditAction;
import dev.techmart.audit.service.AuditLogger;

import dev.techmart.catalog.domain.*;
import dev.techmart.catalog.dto.CatalogDtos.*;
import dev.techmart.catalog.repository.*;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import tools.jackson.core.type.TypeReference;
import dev.techmart.common.web.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final CatalogCache cache;
    private final AuditLogger audit;
    private final Clock clock;

    public ProductService(ProductRepository products, ProductVariantRepository variants,
                          CategoryRepository categories, BrandRepository brands,
                          CatalogCache cache, AuditLogger audit, Clock clock) {
        this.audit = audit;
        this.products = products;
        this.variants = variants;
        this.categories = categories;
        this.brands = brands;
        this.cache = cache;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ đọc

    private static final TypeReference<PageResponse<ProductSummaryResponse>> LIST_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<ProductDetailResponse> DETAIL_TYPE = new TypeReference<>() {};

    /**
     * Tìm kiếm có phân trang, đọc qua cache-aside.
     *
     * <p>Chống N+1: sau khi lấy trang sản phẩm, gom khoảng giá và tồn kho của CẢ trang
     * bằng một query GROUP BY, thay vì để từng Product tự nạp variants của nó.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> search(ProductSearchCriteria criteria, Pageable pageable) {
        return cache.listOrLoad(cacheKeyOf(criteria, pageable), LIST_TYPE, () -> {
            Page<Product> page = products.search(criteria, pageable);
            Map<Long, VariantAggregate> aggregates = loadAggregates(page.getContent());
            return PageResponse.from(page, p -> toSummary(p, aggregates.get(p.getId())));
        });
    }

    /**
     * Khoá cache phải gom TOÀN BỘ thứ ảnh hưởng kết quả. Bỏ sót một tham số là trả nhầm
     * kết quả của truy vấn khác - loại lỗi rất khó lần ra vì nó chỉ xuất hiện khi cache hit.
     * Spec được sắp xếp để {ram,storage} và {storage,ram} ra cùng một khoá.
     */
    private static String cacheKeyOf(ProductSearchCriteria c, Pageable pageable) {
        String spec = c.spec() == null ? "" : c.spec().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining(","));
        return String.join("|",
                String.valueOf(c.keyword()), String.valueOf(c.categoryId()), String.valueOf(c.brandId()),
                String.valueOf(c.minPrice()), String.valueOf(c.maxPrice()), spec,
                String.valueOf(c.inStock()),
                pageable.getPageNumber() + ":" + pageable.getPageSize() + ":" + pageable.getSort());
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getBySlug(String slug) {
        return cache.detailOrLoad(slug, DETAIL_TYPE, () -> products.findBySlug(slug)
                .map(ProductDetailResponse::from)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.PRODUCT_NOT_FOUND, slug)));
    }

    /** So sánh thông số 2-3 máy. Dùng đúng thế mạnh của JSONB, chi phí gần bằng không. */
    @Transactional(readOnly = true)
    public CompareResponse compare(List<Long> ids) {
        if (ids == null || ids.size() < 2 || ids.size() > 3) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chỉ so sánh được 2 hoặc 3 sản phẩm");
        }
        List<Product> found = products.findAllForCompare(ids);
        if (found.size() != ids.size()) {
            throw BusinessException.notFound(ErrorCode.PRODUCT_NOT_FOUND, ids);
        }
        Map<Long, VariantAggregate> aggregates = loadAggregates(found);

        // Hợp của mọi key spec, giữ thứ tự xuất hiện để bảng so sánh đọc được
        Set<String> keys = new LinkedHashSet<>();
        found.forEach(p -> keys.addAll(p.getSpec().keySet()));

        return new CompareResponse(
                List.copyOf(keys),
                found.stream().map(p -> toSummary(p, aggregates.get(p.getId()))).toList(),
                found.stream().map(Product::getSpec).toList());
    }

    // ------------------------------------------------------------------ ghi (ADMIN)

    @Transactional
    public ProductDetailResponse create(CreateProductRequest req) {
        if (products.existsBySlug(req.slug())) {
            throw new BusinessException(ErrorCode.SLUG_ALREADY_EXISTS, "Slug đã tồn tại: " + req.slug());
        }
        Category category = requireCategory(req.categoryId());
        Brand brand = requireBrand(req.brandId());

        Product product = new Product(category, brand, req.name(), req.slug(),
                req.description(), req.thumbnailUrl(), req.spec());

        for (VariantRequest v : req.variants()) {
            requireSkuAvailable(v.sku());
            product.addVariant(new ProductVariant(v.sku(), v.name(), v.price(),
                    v.compareAtPrice(), v.stockQuantity(), v.attributes()));
        }
        if (req.images() != null) {
            int order = 0;
            for (String url : req.images()) {
                product.addImage(new ProductImage(url, order++));
            }
        }
        Product saved = products.save(product);
        cache.invalidate(saved.getSlug());
        audit.record(AuditAction.PRODUCT_CREATED, "PRODUCT", saved.getId(),
                Map.of("name", saved.getName(), "slug", saved.getSlug(), "variants", saved.getVariants().size()));
        return ProductDetailResponse.from(saved);
    }

    @Transactional
    public ProductDetailResponse update(Long id, UpdateProductRequest req) {
        Product product = requireProduct(id);
        if (!product.getSlug().equals(req.slug()) && products.existsBySlug(req.slug())) {
            throw new BusinessException(ErrorCode.SLUG_ALREADY_EXISTS, "Slug đã tồn tại: " + req.slug());
        }
        String previousSlug = product.getSlug();
        Map<String, Object> before = Map.of("name", product.getName(), "slug", previousSlug,
                "status", product.getStatus().name());
        product.update(requireCategory(req.categoryId()), requireBrand(req.brandId()),
                req.name(), req.slug(), req.description(), req.thumbnailUrl(), req.spec(), req.status());
        // Đổi slug thì phải xoá cả khoá cũ, nếu không bản ghi cũ con sống tới hết TTL
        cache.invalidate(previousSlug);
        cache.invalidate(product.getSlug());
        audit.record(AuditAction.PRODUCT_UPDATED, "PRODUCT", id, Map.of("before", before,
                "after", Map.of("name", product.getName(), "slug", product.getSlug(),
                        "status", product.getStatus().name())));
        return ProductDetailResponse.from(product);
    }

    /**
     * Xoá mềm, không xoá cứng.
     *
     * <p>Sản phẩm có thể đã nằm trong đơn hàng cũ; xoá cứng sẽ vi phạm khoá ngoại với
     * order_items hoặc phá lịch sử đơn hàng. Đơn hàng cũ vẫn đọc được vì order_items
     * đã snapshot tên + SKU + giá tại lúc mua.
     */
    @Transactional
    public void softDelete(Long id) {
        Product product = requireProduct(id);
        product.softDelete(clock.instant());
        cache.invalidate(product.getSlug());
        audit.record(AuditAction.PRODUCT_DELETED, "PRODUCT", id, Map.of("slug", product.getSlug()));
        log.info("Đã xoá mềm sản phẩm {} ({})", id, product.getSlug());
    }

    @Transactional
    public VariantResponse addVariant(Long productId, VariantRequest req) {
        Product product = requireProduct(productId);
        requireSkuAvailable(req.sku());
        ProductVariant variant = new ProductVariant(req.sku(), req.name(), req.price(),
                req.compareAtPrice(), req.stockQuantity(), req.attributes());
        product.addVariant(variant);
        products.save(product);
        cache.invalidate(product.getSlug());
        audit.record(AuditAction.VARIANT_CREATED, "PRODUCT", productId,
                Map.of("sku", req.sku(), "price", req.price().toPlainString(), "stock", req.stockQuantity()));
        return VariantResponse.from(variant);
    }

    @Transactional
    public VariantResponse updateVariant(Long variantId, VariantRequest req) {
        ProductVariant variant = requireVariant(variantId);
        if (!variant.getSku().equals(req.sku())) {
            requireSkuAvailable(req.sku());
        }
        String previousPrice = variant.getPrice().toPlainString();
        variant.update(req.name(), req.price(), req.compareAtPrice(), req.attributes());
        audit.record(AuditAction.VARIANT_UPDATED, "VARIANT", variantId, Map.of("sku", variant.getSku(),
                "priceBefore", previousPrice, "priceAfter", variant.getPrice().toPlainString()));
        cache.invalidate(variant.getProduct().getSlug());
        return VariantResponse.from(variant);
    }

    /**
     * Điều chỉnh tồn kho thủ công (nhập hàng, kiểm kê).
     * Ghi log ai sửa và vì sao - kho hàng là thứ phải truy vết được.
     */
    @Transactional
    public VariantResponse adjustStock(Long variantId, AdjustStockRequest req, Long actorId) {
        ProductVariant variant = requireVariant(variantId);
        try {
            variant.adjustStock(req.delta());
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, e.getMessage());
        }
        // Tồn kho đổi thì availableQuantity trên trang danh sách cũng đổi -> phải xoá cache
        cache.invalidate(variant.getProduct().getSlug());
        audit.record(AuditAction.STOCK_ADJUSTED, "VARIANT", variantId, Map.of("sku", variant.getSku(),
                "delta", req.delta(), "stockAfter", variant.getStockQuantity(),
                "reason", req.reason() != null ? req.reason() : ""));
        log.info("User {} điều chỉnh tồn kho variant {} ({}) delta={} lý do='{}'. Tồn mới={}",
                actorId, variantId, variant.getSku(), req.delta(), req.reason(), variant.getStockQuantity());
        return VariantResponse.from(variant);
    }

    // ------------------------------------------------------------------ nội bộ

    private Map<Long, VariantAggregate> loadAggregates(List<Product> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = page.stream().map(Product::getId).toList();
        Map<Long, VariantAggregate> byProduct = new HashMap<>();
        for (VariantAggregate a : variants.aggregateByProductIds(ids)) {
            byProduct.put(a.productId(), a);
        }
        return byProduct;
    }

    private ProductSummaryResponse toSummary(Product p, VariantAggregate agg) {
        return new ProductSummaryResponse(
                p.getId(), p.getName(), p.getSlug(), p.getThumbnailUrl(),
                p.getBrand().getName(), p.getCategory().getName(),
                agg != null ? agg.minPrice() : BigDecimal.ZERO,
                agg != null ? agg.maxPrice() : BigDecimal.ZERO,
                agg != null && agg.availableQuantity() != null ? agg.availableQuantity() : 0L,
                p.getSpec());
    }

    private Product requireProduct(Long id) {
        return products.findDetailById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.PRODUCT_NOT_FOUND, id));
    }

    private ProductVariant requireVariant(Long id) {
        return variants.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.VARIANT_NOT_FOUND, id));
    }

    private Category requireCategory(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.CATEGORY_NOT_FOUND, id));
    }

    private Brand requireBrand(Long id) {
        return brands.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.BRAND_NOT_FOUND, id));
    }

    private void requireSkuAvailable(String sku) {
        if (variants.existsBySku(sku)) {
            throw new BusinessException(ErrorCode.SKU_ALREADY_EXISTS, "SKU đã tồn tại: " + sku);
        }
    }
}
