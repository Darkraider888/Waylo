package com.mehedi.waylo.groups;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripGroupMemberRepository extends JpaRepository<TripGroupMember, Long> {
    Optional<TripGroupMember> findByTripIdAndUserId(Long tripId, Long userId);
    boolean existsByTripIdAndUserIdAndActiveTrue(Long tripId, Long userId);

    @EntityGraph(attributePaths = {"user"})
    List<TripGroupMember> findByTripIdOrderByJoinedAtAsc(Long tripId);
}
