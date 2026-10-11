package com.mehedi.waylo.repository;

import com.mehedi.waylo.entity.Trip;
import com.mehedi.waylo.enums.TripStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    boolean existsByDestinationId(Long destinationId);

    @Override
    @EntityGraph(attributePaths = {"organizer", "destination"})
    Optional<Trip> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trip t WHERE t.id = :id")
    Optional<Trip> lockTripForBooking(@Param("id") Long id);

    @EntityGraph(attributePaths = {"destination", "organizer"})
    @Query("""
        SELECT t FROM Trip t WHERE t.status = :status
          AND t.departureDate > :now
          AND (:destinationId IS NULL OR t.destination.id = :destinationId)
          AND (:term = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(t.description) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(t.destination.name) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(COALESCE(t.destination.district, '')) LIKE LOWER(CONCAT('%', :term, '%')))
        ORDER BY t.departureDate ASC
        """)
    List<Trip> searchPublished(@Param("status") TripStatus status,
                               @Param("term") String term,
                               @Param("destinationId") Long destinationId,
                               @Param("now") java.time.LocalDateTime now);

    @EntityGraph(attributePaths = {"destination", "organizer"})
    List<Trip> findByStatus(TripStatus status);

    @EntityGraph(attributePaths = {"destination", "organizer"})
    Optional<Trip> findByIdAndStatus(
            Long id,
            TripStatus status
    );

    @EntityGraph(attributePaths = {"destination"})
    List<Trip> findByOrganizerIdOrderByIdDesc(
            Long organizerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT t
        FROM Trip t
        WHERE t.id = :id
          AND t.organizer.id = :ownerId
        """)
    Optional<Trip> findOwnedForUpdate(
            @Param("id") Long id,
            @Param("ownerId") Long ownerId
    );
}