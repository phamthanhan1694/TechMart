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
     * Lấy lo event chưa xu ly, khoá dòng da lấy.
     *
     * <p>{@code SKIP LOCKED} cho phép chay nhiều instance worker song song mà không gianh
     * nhau cùng một dòng - mọi worker bỏ qua dòng dạng bi khoá và lấy dòng tiep theo.
     * Đây là cach scale ngang phần xu ly bat dòng bo mà không cần message broker.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.id ASC")
    List<OutboxEvent> lockPendingBatch(Limit limit);

    long countByStatus(OutboxEvent.Status status);

    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, Long aggregateId);
}
