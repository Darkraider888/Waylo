package com.mehedi.waylo.entity;

import com.mehedi.waylo.enums.ApplicationStatus;
import com.mehedi.waylo.enums.DocumentType;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "organizer_applications")
@Getter
@Setter
@NoArgsConstructor
public class OrganizerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @Column(
            name = "organization_name",
            nullable = false,
            length = 150
    )
    private String organizationName;

    @Column(
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String experience;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "document_type",
            nullable = false,
            length = 20
    )
    private DocumentType documentType;

    @Column(
            name = "document_reference",
            length = 255
    )
    private String documentReference;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private ApplicationStatus status =
            ApplicationStatus.PENDING_VERIFICATION;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(
            name = "rejection_reason",
            length = 500
    )
    private String rejectionReason;

    @Column(
            name = "applied_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime appliedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {

        if (appliedAt == null) {
            appliedAt = LocalDateTime.now();
        }
    }
}