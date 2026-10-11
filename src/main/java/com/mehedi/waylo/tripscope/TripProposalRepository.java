package com.mehedi.waylo.tripscope;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TripProposalRepository extends JpaRepository<TripProposal, Long> {
    @EntityGraph(attributePaths = {"destination", "approvedTrip"})
    List<TripProposal> findByApplicantIdOrderByAppliedAtDesc(Long applicantId);

    boolean existsByApplicantIdAndStatus(Long applicantId, TripProposalStatus status);

    boolean existsByApplicantIdAndApprovedTripIdAndStatus(Long applicantId,
                                                           Long approvedTripId,
                                                           TripProposalStatus status);

    boolean existsByApplicantIdAndStatusAndApprovedTripIsNotNull(Long applicantId,
                                                                   TripProposalStatus status);

    @EntityGraph(attributePaths = {"applicant", "destination"})
    List<TripProposal> findByStatusOrderByAppliedAtAsc(TripProposalStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM TripProposal p JOIN FETCH p.applicant WHERE p.id = :id")
    Optional<TripProposal> lockForReview(@Param("id") Long id);
}
