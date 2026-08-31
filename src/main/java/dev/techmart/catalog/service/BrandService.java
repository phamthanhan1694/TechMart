package dev.techmart.catalog.service;

import dev.techmart.catalog.domain.Brand;
import dev.techmart.catalog.dto.CatalogDtos.BrandRequest;
import dev.techmart.catalog.dto.CatalogDtos.BrandResponse;
import dev.techmart.catalog.repository.BrandRepository;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BrandService {

    private final BrandRepository brands;

    public BrandService(BrandRepository brands) {
        this.brands = brands;
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> findAll() {
        return brands.findAll().stream().map(BrandResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BrandResponse findById(Long id) {
        return brands.findById(id).map(BrandResponse::from)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.BRAND_NOT_FOUND, id));
    }

    @Transactional
    public BrandResponse create(BrandRequest req) {
        if (brands.existsBySlug(req.slug())) {
            throw new BusinessException(ErrorCode.SLUG_ALREADY_EXISTS, "Slug da ton tai: " + req.slug());
        }
        return BrandResponse.from(brands.save(new Brand(req.name(), req.slug())));
    }

    @Transactional
    public BrandResponse update(Long id, BrandRequest req) {
        Brand brand = brands.findById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.BRAND_NOT_FOUND, id));
        if (!brand.getSlug().equals(req.slug()) && brands.existsBySlug(req.slug())) {
            throw new BusinessException(ErrorCode.SLUG_ALREADY_EXISTS, "Slug da ton tai: " + req.slug());
        }
        brand.rename(req.name(), req.slug());
        return BrandResponse.from(brand);
    }
}
