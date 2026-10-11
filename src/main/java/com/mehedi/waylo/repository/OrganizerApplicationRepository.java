package com.mehedi.waylo.repository;

import com.mehedi.waylo.entity.OrganizerApplication;
import com.mehedi.waylo.enums.ApplicationStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrganizerApplicationRepository
        extends JpaRepository<OrganizerApplication, Long> {

    Optional<OrganizerApplication>
    findFirstByUserIdOrderByAppliedAtDesc(
            Long userId
    );

    boolean existsByUserIdAndStatus(
            Long userId,
            ApplicationStatus status
    );

    @EntityGraph(attributePaths = {"user"})
    List<OrganizerApplication>
    findByStatusOrderByAppliedAtAsc(
            ApplicationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT application
           FROM OrganizerApplication application
           JOIN FETCH application.user
           WHERE application.id = :id
           """)
    Optional<OrganizerApplication> findForReview(
            @Param("id") Long id
    );
}