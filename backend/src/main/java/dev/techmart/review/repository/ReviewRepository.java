package dev.techmart.review.repository;

import dev.techmart.review.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Nạp kèm user để hiển thị ten người đánh giá - không thì 20 review = 21 query. */
    @EntityGraph(attributePaths = {"user"})
    Page<Review> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    boolean existsByOrderItemId(Long orderItemId);

    Optional<Review> findByOrderItemId(Long orderItemId);

    /**
     * Phan bo so sao, tinh bằng một query GROUP BY.
     * Không nạp het review ve app rồi đếm - man hinh sản phẩm hot có thể có hàng nghìn review.
     */
    @Query("""
            SELECT r.rating, COUNT(r) FROM Review r
             WHERE r.product.id = :productId
             GROUP BY r.rating
            """)
    List<Object[]> ratingDistribution(@Param("productId") Long productId);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.product.id = :productId")
    double averageRating(@Param("productId") Long productId);
}
