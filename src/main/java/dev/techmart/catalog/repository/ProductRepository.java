package dev.techmart.catalog.repository;

import dev.techmart.catalog.domain.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    /** Nap kem variant + anh + brand + category trong MOT query - chong N+1 o trang chi tiet. */
    @EntityGraph(attributePaths = {"variants", "images", "brand", "category"})
    Optional<Product> findBySlug(String slug);

    @EntityGraph(attributePaths = {"variants", "images", "brand", "category"})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findDetailById(@Param("id") Long id);

    /** So sanh 2-3 san pham: mot query, khong lap N lan. */
    @EntityGraph(attributePaths = {"brand", "category"})
    @Query("SELECT p FROM Product p WHERE p.id IN :ids")
    List<Product> findAllForCompare(@Param("ids") Collection<Long> ids);

    boolean existsBySlug(String slug);

    long countByCategoryId(Long categoryId);
}
