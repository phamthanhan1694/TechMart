package dev.techmart.catalog.repository;

import dev.techmart.catalog.domain.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
