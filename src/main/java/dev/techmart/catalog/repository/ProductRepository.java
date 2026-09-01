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
     * Nap kem variant + brand + category cho trang chi tiet.
     *
     * <p><b>Co chu y khong nap kem {@code images}:</b> Hibernate khong JOIN FETCH duoc hai
     * collection kieu List (bag) trong cung mot query - no nem MultipleBagFetchException,
     * vi tich Descartes cua hai bag khong the tach nguoc ra duoc. Anh duoc nap lazy bang
     * mot query rieng: trang chi tiet ton 2 query thay vi 1, doi lai khong phai doi
     * mo hinh entity sang Set. Neu can gop that thi doi ca hai sang Set hoac dung
     * @Fetch(SUBSELECT).
     */
    @EntityGraph(attributePaths = {"variants", "brand", "category"})
    Optional<Product> findBySlug(String slug);

    @EntityGraph(attributePaths = {"variants", "brand", "category"})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findDetailById(@Param("id") Long id);

    /** So sanh 2-3 san pham: mot query, khong lap N lan. */
    @EntityGraph(attributePaths = {"brand", "category"})
    @Query("SELECT p FROM Product p WHERE p.id IN :ids")
    List<Product> findAllForCompare(@Param("ids") Collection<Long> ids);

    boolean existsBySlug(String slug);

    long countByCategoryId(Long categoryId);
}
