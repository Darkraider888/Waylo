package com.mehedi.waylo.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    @Query("SELECT a.booking.trip.id FROM PaymentAttempt a WHERE a.transactionId = :reference")
    Optional<Long> findTripIdByTransactionId(@Param("reference") String reference);

    @EntityGraph(attributePaths = {"booking"})
    Optional<PaymentAttempt> findByTransactionId(String transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PaymentAttempt a WHERE a.transactionId = :reference")
    Optional<PaymentAttempt> lockByReference(@Param("reference") String reference);
    @EntityGraph(attributePaths = {"booking", "booking.trip", "booking.traveler"})
    List<PaymentAttempt> findByStatusOrderByCreatedAtDesc(String status);

    boolean existsByBookingIdAndPaymentKindAndStatusAndCreatedAtAfter(
            Long bookingId, String paymentKind, String status, LocalDateTime after);
}
