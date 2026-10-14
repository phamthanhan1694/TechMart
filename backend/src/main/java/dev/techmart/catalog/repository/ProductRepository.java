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

    /**
     * Nạp kèm variant + brand + category cho trang chi tiết.
     *
     * <p><b>Có chu y không nạp kèm {@code images}:</b> Hibernate không JOIN FETCH được hai
     * collection kieu List (bag) trong cùng một query - no ném MultipleBagFetchException,
     * vì tích Descartes của hai bag không thể tách ngược ra được. Anh được nạp lazy bằng
     * một query riêng: trang chi tiết tốn 2 query thay vì 1, đổi lại không phải đổi
     * mo hinh entity sang Set. Nếu cần gop thật thì đổi ca hai sang Set hoac dùng
     * @Fetch(SUBSELECT).
     */
    @EntityGraph(attributePaths = {"variants", "brand", "category"})
    Optional<Product> findBySlug(String slug);

    @EntityGraph(attributePaths = {"variants", "brand", "category"})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findDetailById(@Param("id") Long id);

    /** So sanh 2-3 sản phẩm: một query, không lap N lan. */
    @EntityGraph(attributePaths = {"brand", "category"})
    @Query("SELECT p FROM Product p WHERE p.id IN :ids")
    List<Product> findAllForCompare(@Param("ids") Collection<Long> ids);

    boolean existsBySlug(String slug);

    long countByCategoryId(Long categoryId);
}
