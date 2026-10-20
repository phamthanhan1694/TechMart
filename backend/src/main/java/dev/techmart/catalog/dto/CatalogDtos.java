package dev.techmart.catalog.dto;

import dev.techmart.catalog.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    // ------------------------------------------------------------- Thương hiệu / Danh mục

    public record BrandResponse(Long id, String name, String slug) {
        public static BrandResponse from(Brand b) {
            return new BrandResponse(b.getId(), b.getName(), b.getSlug());
        }
    }

    public record BrandRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 120) @Pattern(regexp = "^[a-z0-9-]+$",
                    message = "Slug chỉ gồm chữ thường, số và dấu gạch ngang") String slug
    ) {}

    public record CategoryResponse(Long id, String name, String slug, Long parentId, String parentName) {
        public static CategoryResponse from(Category c) {
            Category p = c.getParent();
            return new CategoryResponse(c.getId(), c.getName(), c.getSlug(),
                    p != null ? p.getId() : null, p != null ? p.getName() : null);
        }
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 120) @Pattern(regexp = "^[a-z0-9-]+$") String slug,
            Long parentId
    ) {}

    // ------------------------------------------------------------- Variant

    @Schema(description = "Phiên bản bán được của sản phẩm")
    public record VariantResponse(
            Long id,
            String sku,
            String name,
            BigDecimal price,
            BigDecimal compareAtPrice,
            @Schema(description = "Hàng thực sự mua được = tồn kho - đang giữ") int availableQuantity,
            Map<String, Object> attributes
    ) {
        public static VariantResponse from(ProductVariant v) {
            return new VariantResponse(v.getId(), v.getSku(), v.getName(), v.getPrice(),
                    v.getCompareAtPrice(), v.availableQuantity(), v.getAttributes());
        }
    }

    public record VariantRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 150) @Schema(example = "256GB - Titan đen") String name,
            @NotNull @DecimalMin(value = "0.01") BigDecimal price,
            @DecimalMin(value = "0.01") BigDecimal compareAtPrice,
            @NotNull @Min(0) Integer stockQuantity,
            @Schema(example = "{\"color\": \"Titan đen\", \"storage\": \"256GB\"}")
            Map<String, Object> attributes
    ) {}

    public record AdjustStockRequest(
            @NotNull @Schema(example = "-5", description = "Âm là giảm, dương là tăng") Integer delta,
            @NotBlank @Size(max = 255) String reason
    ) {}

    // ------------------------------------------------------------- Product

    @Schema(description = "Sản phẩm rút gọn - dùng ở trang danh sách")
    public record ProductSummaryResponse(
            Long id,
            String name,
            String slug,
            String thumbnailUrl,
            String brandName,
            String categoryName,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            long availableQuantity,
            Map<String, Object> spec
    ) {}

    @Schema(description = "Sản phẩm đầy đủ - dùng ở trang chi tiết")
    public record ProductDetailResponse(
            Long id,
            String name,
            String slug,
            String description,
            String thumbnailUrl,
            BrandResponse brand,
            CategoryResponse category,
            Map<String, Object> spec,
            ProductStatus status,
            List<VariantResponse> variants,
            List<String> images
    ) {
        public static ProductDetailResponse from(Product p) {
            return new ProductDetailResponse(
                    p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getThumbnailUrl(),
                    BrandResponse.from(p.getBrand()), CategoryResponse.from(p.getCategory()),
                    p.getSpec(), p.getStatus(),
                    p.getVariants().stream().map(VariantResponse::from).toList(),
                    p.getImages().stream().map(ProductImage::getUrl).toList());
        }
    }

    public record CreateProductRequest(
            @NotNull Long categoryId,
            @NotNull Long brandId,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 280) @Pattern(regexp = "^[a-z0-9-]+$") String slug,
            @Size(max = 20000) String description,
            @Size(max = 500) String thumbnailUrl,
            @Schema(example = "{\"ram\": \"8GB\", \"storage\": \"256GB\", \"chipset\": \"A17 Pro\"}")
            Map<String, Object> spec,
            @Valid @NotEmpty(message = "Sản phẩm phải có ít nhất một phiên bản")
            List<VariantRequest> variants,
            List<@Size(max = 500) String> images
    ) {}

    public record UpdateProductRequest(
            @NotNull Long categoryId,
            @NotNull Long brandId,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 280) @Pattern(regexp = "^[a-z0-9-]+$") String slug,
            @Size(max = 20000) String description,
            @Size(max = 500) String thumbnailUrl,
            Map<String, Object> spec,
            ProductStatus status
    ) {}

    @Schema(description = "So sánh thông số nhiều sản phẩm. specKeys là hợp của mọi key xuất hiện.")
    public record CompareResponse(
            List<String> specKeys,
            List<ProductSummaryResponse> products,
            List<Map<String, Object>> specs
    ) {}
}
