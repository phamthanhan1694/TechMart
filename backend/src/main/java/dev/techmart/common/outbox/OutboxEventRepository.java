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
     * Lấy lô event chưa xử lý, khoá dòng đã lấy.
     *
     * <p>{@code SKIP LOCKED} cho phép chạy nhiều instance worker song song mà không giành
     * nhau cùng một dòng - mọi worker bỏ qua dòng đang bị khoá và lấy dòng tiếp theo.
     * Đây là cách scale ngang phần xử lý bất đồng bộ mà không cần message broker.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.id ASC")
    List<OutboxEvent> lockPendingBatch(Limit limit);

    long countByStatus(OutboxEvent.Status status);

    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, Long aggregateId);
}
