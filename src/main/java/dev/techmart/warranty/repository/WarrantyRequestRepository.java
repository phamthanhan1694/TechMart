package dev.techmart.warranty.repository;

import dev.techmart.warranty.domain.WarrantyRequest;
import dev.techmart.warranty.domain.WarrantyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WarrantyRequestRepository extends JpaRepository<WarrantyRequest, Long> {

    @Query("""
            SELECT w FROM WarrantyRequest w
              JOIN FETCH w.serial s
              JOIN FETCH s.variant v
              JOIN FETCH v.product
             WHERE w.id = :id
            """)
    Optional<WarrantyRequest> findDetailById(@Param("id") Long id);

    @Query(value = """
            SELECT w FROM WarrantyRequest w
              JOIN FETCH w.serial s
              JOIN FETCH s.variant v
              JOIN FETCH v.product
             WHERE (:userId IS NULL OR w.user.id = :userId)
               AND (:status IS NULL OR w.status = :status)
             ORDER BY w.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(w) FROM WarrantyRequest w
             WHERE (:userId IS NULL OR w.user.id = :userId)
               AND (:status IS NULL OR w.status = :status)
            """)
    Page<WarrantyRequest> search(@Param("userId") Long userId,
                                 @Param("status") WarrantyStatus status,
                                 Pageable pageable);

    /** Mot serial khong nen co hai yeu cau bao hanh cung mo. */
    boolean existsBySerialIdAndStatusIn(Long serialId, java.util.Collection<WarrantyStatus> statuses);
}
