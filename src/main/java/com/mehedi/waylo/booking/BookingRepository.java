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

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("SELECT b.id FROM Booking b WHERE b.status='PENDING_PAYMENT' AND b.holdExpiresAt <= :now ORDER BY b.id")
    List<Long> expiredPendingIds(@Param("now") LocalDateTime now, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT b.trip.id FROM Booking b WHERE b.id=:id")
    Optional<Long> tripIdForBooking(@Param("id") Long id);

    @Query("SELECT b.trip.id FROM Booking b WHERE b.id = :id AND b.traveler.id = :travelerId")
    Optional<Long> findTripIdForTraveler(@Param("id") Long id, @Param("travelerId") Long travelerId);

    @Query("""
        SELECT COALESCE(SUM(b.seatCount), 0) FROM Booking b
        WHERE b.trip.id = :tripId AND
          (b.status = 'CONFIRMED' OR
           (b.status = 'PENDING_PAYMENT' AND b.holdExpiresAt > :now))
        """)
    Long heldSeats(@Param("tripId") Long tripId, @Param("now") LocalDateTime now);

    @Query("""
        SELECT COUNT(b) FROM Booking b WHERE b.trip.id = :tripId
          AND b.traveler.id = :userId AND
          (b.status = 'CONFIRMED' OR b.status = 'REVIEW' OR
           (b.status = 'PENDING_PAYMENT' AND b.holdExpiresAt > :now))
        """)
    long existingActive(@Param("tripId") Long tripId,
                        @Param("userId") Long userId,
                        @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> lockById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"trip", "trip.destination", "traveler"})
    Optional<Booking> findByIdAndTravelerId(Long id, Long travelerId);

    @EntityGraph(attributePaths = {"trip", "trip.destination"})
    List<Booking> findByTravelerIdOrderByCreatedAtDesc(Long travelerId);
    @EntityGraph(attributePaths = {"trip", "traveler"})
    List<Booking> findByStatusOrderByCreatedAtDesc(String status);

    /** Chat entitlement requires BOTH a confirmed booking AND a verified advance transaction. */
    @Query("""
        SELECT COUNT(b) FROM Booking b
        WHERE b.trip.id = :tripId AND b.traveler.id = :travelerId
          AND b.status = 'CONFIRMED' AND EXISTS (
            SELECT a.id FROM PaymentAttempt a WHERE a.booking = b
              AND a.paymentKind = 'ADVANCE' AND a.status = 'VERIFIED')
        """)
    long verifiedGroupBookings(@Param("tripId") Long tripId,
                               @Param("travelerId") Long travelerId);

    @EntityGraph(attributePaths = {"traveler"})
    List<Booking> findByTripIdAndStatusOrderByCreatedAtAsc(Long tripId, String status);
}
