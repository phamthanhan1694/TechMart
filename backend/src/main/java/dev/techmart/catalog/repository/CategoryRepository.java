package dev.techmart.catalog.repository;

import dev.techmart.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Category> findAllByParentIsNull();

    List<Category> findAllByParentId(Long parentId);
}
