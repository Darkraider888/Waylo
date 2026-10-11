package com.mehedi.waylo.groups;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TripGroupMessageRepository extends JpaRepository<TripGroupMessage, Long> {
    @EntityGraph(attributePaths = {"sender"})
    List<TripGroupMessage> findByTripIdOrderByIdDesc(Long tripId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    List<TripGroupMessage> findByTripIdAndIdGreaterThanOrderByIdAsc(
            Long tripId, Long afterId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    List<TripGroupMessage> findByTripIdAndIdLessThanOrderByIdDesc(Long tripId, Long beforeId, Pageable pageable);

    boolean existsByTripIdAndSenderIdAndCreatedAtAfter(
            Long tripId, Long senderId, LocalDateTime after);
}
