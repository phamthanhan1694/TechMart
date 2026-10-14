package dev.techmart.payment.repository;

import dev.techmart.payment.domain.Payment;
import dev.techmart.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"order"})
    Optional<Payment> findByTransactionRef(String transactionRef);

    @EntityGraph(attributePaths = {"order"})
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
