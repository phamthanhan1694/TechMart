package dev.techmart.catalog.web;

import dev.techmart.catalog.dto.CatalogDtos.*;
import dev.techmart.catalog.service.BrandService;
import dev.techmart.catalog.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalog - Danh muc & Thuong hieu")
public class TaxonomyController {

    private final CategoryService categoryService;
    private final BrandService brandService;

    public TaxonomyController(CategoryService categoryService, BrandService brandService) {
        this.categoryService = categoryService;
        this.brandService = brandService;
    }

    // --- Danh mục ---

    @GetMapping("/categories")
    @SecurityRequirements
    @Operation(summary = "Danh sach danh muc")
    public List<CategoryResponse> categories() {
        return categoryService.findAll();
    }

    @GetMapping("/categories/{id}")
    @SecurityRequirements
    @Operation(summary = "Chi tiet danh muc")
    public CategoryResponse category(@PathVariable Long id) {
        return categoryService.findById(id);
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tao danh muc")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(req));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cap nhat danh muc")
    public CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        return categoryService.update(id, req);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xoa danh muc", description = "Chi xoa duoc khi khong con san pham va danh muc con.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
    }

    // --- Thương hiệu ---

    @GetMapping("/brands")
    @SecurityRequirements
    @Operation(summary = "Danh sach thuong hieu")
    public List<BrandResponse> brands() {
        return brandService.findAll();
    }

    @GetMapping("/brands/{id}")
    @SecurityRequirements
    @Operation(summary = "Chi tiet thuong hieu")
    public BrandResponse brand(@PathVariable Long id) {
        return brandService.findById(id);
    }

    @PostMapping("/brands")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tao thuong hieu")
    public ResponseEntity<BrandResponse> createBrand(@Valid @RequestBody BrandRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(brandService.create(req));
    }

    @PutMapping("/brands/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cap nhat thuong hieu")
    public BrandResponse updateBrand(@PathVariable Long id, @Valid @RequestBody BrandRequest req) {
        return brandService.update(id, req);
    }
}
