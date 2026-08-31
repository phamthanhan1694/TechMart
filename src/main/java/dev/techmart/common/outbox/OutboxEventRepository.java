package dev.techmart.common.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Lay lo event chua xu ly, khoa dong da lay.
     *
     * <p>{@code SKIP LOCKED} cho phep chay nhieu instance worker song song ma khong gianh
     * nhau cung mot dong - moi worker bo qua dong dang bi khoa va lay dong tiep theo.
     * Day la cach scale ngang phan xu ly bat dong bo ma khong can message broker.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.id ASC")
    List<OutboxEvent> lockPendingBatch(Limit limit);

    long countByStatus(OutboxEvent.Status status);

    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, Long aggregateId);
}
