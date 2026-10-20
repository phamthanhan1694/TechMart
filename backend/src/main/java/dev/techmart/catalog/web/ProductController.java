package dev.techmart.catalog.web;

import dev.techmart.catalog.dto.CatalogDtos.*;
import dev.techmart.catalog.repository.ProductRepositoryImpl;
import dev.techmart.catalog.repository.ProductSearchCriteria;
import dev.techmart.catalog.service.ProductService;
import dev.techmart.common.web.PageResponse;
import dev.techmart.common.web.PageableSanitizer;
import dev.techmart.identity.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalog", description = "Sản phẩm, phiên bản, tìm kiếm và lọc")
public class ProductController {

    private static final String SPEC_PREFIX = "spec.";

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    @SecurityRequirements
    @Operation(summary = "Tìm kiếm sản phẩm",
            description = """
                    Phân trang, sắp xếp, lọc theo danh mục / hãng / khoảng giá / còn hàng,
                    và lọc theo thông số kỹ thuật qua tham số dạng `spec.<key>`.

                    Ví dụ: `/api/v1/products?keyword=iphone&spec.ram=8GB&spec.storage=256GB&sort=price,asc`

                    Các key spec được phép: ram, storage, cpu, chipset, gpu, screen, os,
                    camera, battery, refreshRate, weight, releaseYear.
                    Sắp xếp được theo: name, price, createdAt. `size` tối đa 100.
                    """)
    public PageResponse<ProductSummaryResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @Parameter(hidden = true) @RequestParam Map<String, String> allParams,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        var criteria = new ProductSearchCriteria(
                keyword, categoryId, brandId, minPrice, maxPrice, extractSpecFilter(allParams), inStock);

        return productService.search(criteria, PageableSanitizer.sanitize(pageable));
    }

    @GetMapping("/products/compare")
    @SecurityRequirements
    @Operation(summary = "So sánh thông số 2-3 sản phẩm")
    public CompareResponse compare(@RequestParam List<Long> ids) {
        return productService.compare(ids);
    }

    @GetMapping("/products/{slug}")
    @SecurityRequirements
    @Operation(summary = "Chi tiết sản phẩm theo slug",
            description = "Tra kèm tất cả phiên bản và số hàng thực sự mua được của từng phiên bản.")
    public ProductDetailResponse getBySlug(@PathVariable String slug) {
        return productService.getBySlug(slug);
    }

    // ------------------------------------------------------------------ ADMIN

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo sản phẩm kèm phiên bản đầu tiên")
    public ResponseEntity<ProductDetailResponse> create(@Valid @RequestBody CreateProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(req));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật sản phẩm")
    public ProductDetailResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest req) {
        return productService.update(id, req);
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xoá sản phẩm (xoá mềm)",
            description = "Set deleted_at chứ không xoá cứng: sản phẩm có thể đang nằm trong đơn hàng cũ.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.softDelete(id);
    }

    @PostMapping("/products/{id}/variants")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thêm phiên bản cho sản phẩm")
    public ResponseEntity<VariantResponse> addVariant(@PathVariable Long id,
                                                      @Valid @RequestBody VariantRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.addVariant(id, req));
    }

    @PutMapping("/variants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật phiên bản")
    public VariantResponse updateVariant(@PathVariable Long id, @Valid @RequestBody VariantRequest req) {
        return productService.updateVariant(id, req);
    }

    @PatchMapping("/variants/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "Điều chỉnh tồn kho thủ công",
            description = "delta âm là giảm, dương là tăng. Mỗi lần điều chỉnh đều được ghi log kèm lý do.")
    public VariantResponse adjustStock(@PathVariable Long id,
                                       @Valid @RequestBody AdjustStockRequest req,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        return productService.adjustStock(id, req, principal.id());
    }

    /**
     * Tách các tham số dạng {@code spec.<key>} ra khỏi query string.
     * Chỉ giữ key nằm trong whitelist - key là tự do từ client, không được để đi thẳng xuống SQL.
     */
    private Map<String, String> extractSpecFilter(Map<String, String> allParams) {
        Map<String, String> spec = new HashMap<>();
        allParams.forEach((rawKey, value) -> {
            if (!rawKey.startsWith(SPEC_PREFIX) || value == null || value.isBlank()) {
                return;
            }
            String key = rawKey.substring(SPEC_PREFIX.length());
            if (ProductRepositoryImpl.allowedSpecKeys().contains(key)) {
                spec.put(key, value);
            }
        });
        return spec;
    }
}
