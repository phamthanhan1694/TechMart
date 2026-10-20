package dev.techmart.catalog.service;

import dev.techmart.catalog.domain.Category;
import dev.techmart.catalog.dto.CatalogDtos.CategoryRequest;
import dev.techmart.catalog.dto.CatalogDtos.CategoryResponse;
import dev.techmart.catalog.repository.CategoryRepository;
import dev.techmart.catalog.repository.ProductRepository;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categories.findAll().stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return categories.findById(id).map(CategoryResponse::from)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.CATEGORY_NOT_FOUND, id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        if (categories.existsBySlug(req.slug())) {
            throw new BusinessException(ErrorCode.SLUG_ALREADY_EXISTS, "Slug đã tồn tại: " + req.slug());
        }
        return CategoryResponse.from(categories.save(
                new Category(req.name(), req.slug(), resolveParent(req.parentId(), null))));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category category = categories.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.CATEGORY_NOT_FOUND, id));
        if (!category.getSlug().equals(req.slug()) && categories.existsBySlug(req.slug())) {
            throw new BusinessException(ErrorCode.SLUG_ALREADY_EXISTS, "Slug đã tồn tại: " + req.slug());
        }
        category.update(req.name(), req.slug(), resolveParent(req.parentId(), id));
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = categories.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.CATEGORY_NOT_FOUND, id));
        if (products.countByCategoryId(id) > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_EMPTY,
                    "Danh mục vẫn còn sản phẩm, không thể xoá");
        }
        if (!categories.findAllByParentId(id).isEmpty()) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_EMPTY, "Danh mục vẫn còn danh mục con");
        }
        categories.delete(category);
    }

    private Category resolveParent(Long parentId, Long selfId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(selfId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Danh mục không thể là cha của chính nó");
        }
        return categories.findById(parentId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.CATEGORY_NOT_FOUND, parentId));
    }
}
