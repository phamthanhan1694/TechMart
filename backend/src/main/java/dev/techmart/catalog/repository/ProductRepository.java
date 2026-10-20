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
     * <p><b>Cố ý không nạp kèm {@code images}:</b> Hibernate không JOIN FETCH được hai
     * collection kiểu List (bag) trong cùng một query - nó ném MultipleBagFetchException,
     * vì tích Descartes của hai bag không thể tách ngược ra được. Ảnh được nạp lazy bằng
     * một query riêng: trang chi tiết tốn 2 query thay vì 1, đổi lại không phải đổi
     * mô hình entity sang Set. Nếu cần gộp thật thì đổi cả hai sang Set hoặc dùng
     * @Fetch(SUBSELECT).
     */
    @EntityGraph(attributePaths = {"variants", "brand", "category"})
    Optional<Product> findBySlug(String slug);

    @EntityGraph(attributePaths = {"variants", "brand", "category"})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findDetailById(@Param("id") Long id);

    /** So sánh 2-3 sản phẩm: một query, không lặp N lần. */
    @EntityGraph(attributePaths = {"brand", "category"})
    @Query("SELECT p FROM Product p WHERE p.id IN :ids")
    List<Product> findAllForCompare(@Param("ids") Collection<Long> ids);

    boolean existsBySlug(String slug);

    long countByCategoryId(Long categoryId);
}
