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
@Tag(name = "Catalog", description = "San pham, phien ban, tim kiem va loc")
public class ProductController {

    private static final String SPEC_PREFIX = "spec.";

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    @SecurityRequirements
    @Operation(summary = "Tim kiem san pham",
            description = """
                    Phan trang, sap xep, loc theo danh muc / hang / khoang gia / con hang,
                    va loc theo thong so ky thuat qua tham so dang `spec.<key>`.

                    Vi du: `/api/v1/products?keyword=iphone&spec.ram=8GB&spec.storage=256GB&sort=price,asc`

                    Cac key spec duoc phep: ram, storage, cpu, chipset, gpu, screen, os,
                    camera, battery, refreshRate, weight, releaseYear.
                    Sap xep duoc theo: name, price, createdAt. `size` toi da 100.
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
    @Operation(summary = "So sanh thong so 2-3 san pham")
    public CompareResponse compare(@RequestParam List<Long> ids) {
        return productService.compare(ids);
    }

    @GetMapping("/products/{slug}")
    @SecurityRequirements
    @Operation(summary = "Chi tiet san pham theo slug",
            description = "Tra kem tat ca phien ban va so hang thuc su mua duoc cua tung phien ban.")
    public ProductDetailResponse getBySlug(@PathVariable String slug) {
        return productService.getBySlug(slug);
    }

    // ------------------------------------------------------------------ ADMIN

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tao san pham kem phien ban dau tien")
    public ResponseEntity<ProductDetailResponse> create(@Valid @RequestBody CreateProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(req));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cap nhat san pham")
    public ProductDetailResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest req) {
        return productService.update(id, req);
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xoa san pham (xoa mem)",
            description = "Set deleted_at chu khong xoa cung: san pham co the dang nam trong don hang cu.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.softDelete(id);
    }

    @PostMapping("/products/{id}/variants")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Them phien ban cho san pham")
    public ResponseEntity<VariantResponse> addVariant(@PathVariable Long id,
                                                      @Valid @RequestBody VariantRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.addVariant(id, req));
    }

    @PutMapping("/variants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cap nhat phien ban")
    public VariantResponse updateVariant(@PathVariable Long id, @Valid @RequestBody VariantRequest req) {
        return productService.updateVariant(id, req);
    }

    @PatchMapping("/variants/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "Dieu chinh ton kho thu cong",
            description = "delta am la giam, duong la tang. Moi lan dieu chinh deu duoc ghi log kem ly do.")
    public VariantResponse adjustStock(@PathVariable Long id,
                                       @Valid @RequestBody AdjustStockRequest req,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        return productService.adjustStock(id, req, principal.id());
    }

    /**
     * Tach các tham sở dạng {@code spec.<key>} ra khoi query string.
     * Chi giữ key nằm trong whitelist - key là từ đó từ client, không được để di thang xuong SQL.
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
