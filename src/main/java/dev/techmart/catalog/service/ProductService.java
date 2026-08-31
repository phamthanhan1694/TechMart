package dev.techmart.catalog.service;

import dev.techmart.catalog.domain.*;
import dev.techmart.catalog.dto.CatalogDtos.*;
import dev.techmart.catalog.repository.*;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
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
    private final Clock clock;

    public ProductService(ProductRepository products, ProductVariantRepository variants,
                          CategoryRepository categories, BrandRepository brands, Clock clock) {
        this.products = products;
        this.variants = variants;
        this.categories = categories;
        this.brands = brands;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ doc

    /**
     * Tim kiem co phan trang.
     *
     * <p>Chong N+1: sau khi lay trang san pham, gom khoang gia va ton kho cua CA trang
     * bang mot query GROUP BY, thay vi de tung Product tu nap variants cua no.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> search(ProductSearchCriteria criteria, Pageable pageable) {
        Page<Product> page = products.search(criteria, pageable);
        Map<Long, VariantAggregate> aggregates = loadAggregates(page.getContent());
        return PageResponse.from(page, p -> toSummary(p, aggregates.get(p.getId())));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getBySlug(String slug) {
        return products.findBySlug(slug)
                .map(ProductDetailResponse::from)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.PRODUCT_NOT_FOUND, slug));
    }

    /** So sanh thong so 2-3 may. Dung dung the manh cua JSONB, chi phi gan bang khong. */
    @Transactional(readOnly = true)
    public CompareResponse compare(List<Long> ids) {
        if (ids == null || ids.size() < 2 || ids.size() > 3) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chi so sanh duoc 2 hoac 3 san pham");
        }
        List<Product> found = products.findAllForCompare(ids);
        if (found.size() != ids.size()) {
            throw BusinessException.notFound(ErrorCode.PRODUCT_NOT_FOUND, ids);
        }
        Map<Long, VariantAggregate> aggregates = loadAggregates(found);

        // Hop cua moi key spec, giu thu tu xuat hien de bang so sanh doc duoc
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
            throw new BusinessException(ErrorCode.SLUG_ALREADY_EXISTS, "Slug da ton tai: " + req.slug());
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
        return ProductDetailResponse.from(products.save(product));
    }

    @Transactional
    public ProductDetailResponse update(Long id, UpdateProductRequest req) {
        Product product = requireProduct(id);
        if (!product.getSlug().equals(req.slug()) && products.existsBySlug(req.slug())) {
            throw new BusinessException(ErrorCode.SLUG_ALREADY_EXISTS, "Slug da ton tai: " + req.slug());
        }
        product.update(requireCategory(req.categoryId()), requireBrand(req.brandId()),
                req.name(), req.slug(), req.description(), req.thumbnailUrl(), req.spec(), req.status());
        return ProductDetailResponse.from(product);
    }

    /**
     * Xoa mem, khong xoa cung.
     *
     * <p>San pham co the da nam trong don hang cu; xoa cung se vi pham khoa ngoai voi
     * order_items hoac pha lich su don hang. Don hang cu van doc duoc vi order_items
     * da snapshot ten + SKU + gia tai luc mua.
     */
    @Transactional
    public void softDelete(Long id) {
        Product product = requireProduct(id);
        product.softDelete(clock.instant());
        log.info("Da xoa mem san pham {} ({})", id, product.getSlug());
    }

    @Transactional
    public VariantResponse addVariant(Long productId, VariantRequest req) {
        Product product = requireProduct(productId);
        requireSkuAvailable(req.sku());
        ProductVariant variant = new ProductVariant(req.sku(), req.name(), req.price(),
                req.compareAtPrice(), req.stockQuantity(), req.attributes());
        product.addVariant(variant);
        products.save(product);
        return VariantResponse.from(variant);
    }

    @Transactional
    public VariantResponse updateVariant(Long variantId, VariantRequest req) {
        ProductVariant variant = requireVariant(variantId);
        if (!variant.getSku().equals(req.sku())) {
            requireSkuAvailable(req.sku());
        }
        variant.update(req.name(), req.price(), req.compareAtPrice(), req.attributes());
        return VariantResponse.from(variant);
    }

    /**
     * Dieu chinh ton kho thu cong (nhap hang, kiem ke).
     * Ghi log ai sua va vi sao - kho hang la thu phai truy vet duoc.
     */
    @Transactional
    public VariantResponse adjustStock(Long variantId, AdjustStockRequest req, Long actorId) {
        ProductVariant variant = requireVariant(variantId);
        try {
            variant.adjustStock(req.delta());
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, e.getMessage());
        }
        log.info("User {} dieu chinh ton kho variant {} ({}) delta={} ly do='{}'. Ton moi={}",
                actorId, variantId, variant.getSku(), req.delta(), req.reason(), variant.getStockQuantity());
        return VariantResponse.from(variant);
    }

    // ------------------------------------------------------------------ noi bo

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
            throw new BusinessException(ErrorCode.SKU_ALREADY_EXISTS, "SKU da ton tai: " + sku);
        }
    }
}
