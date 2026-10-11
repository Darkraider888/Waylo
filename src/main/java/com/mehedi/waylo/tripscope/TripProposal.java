package com.mehedi.waylo.tripscope;

import com.mehedi.waylo.entity.Destination;
import com.mehedi.waylo.entity.Trip;
import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.enums.DocumentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trip_proposals")
@Getter @Setter @NoArgsConstructor
public class TripProposal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id")
    private Destination destination;

    // A pending application can suggest a place not yet in the public destination table.
    // Admin review resolves these fields into a real Destination before creating a trip.
    @Column(name = "suggested_destination_name", length = 150)
    private String suggestedDestinationName;

    @Column(name = "suggested_district", length = 120)
    private String suggestedDistrict;

    @Column(name = "suggested_country", length = 120)
    private String suggestedCountry;

    @Column(name = "suggested_description", length = 1000)
    private String suggestedDescription;

    @Column(name = "suggested_latitude", precision = 10, scale = 7)
    private BigDecimal suggestedLatitude;

    @Column(name = "suggested_longitude", precision = 10, scale = 7)
    private BigDecimal suggestedLongitude;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_trip_id", unique = true)
    private Trip approvedTrip;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "departure_date", nullable = false)
    private LocalDateTime departureDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false, length = 1500)
    private String motivation;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TripProposalStatus status = TripProposalStatus.PENDING;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    void created() {
        if (appliedAt == null) appliedAt = LocalDateTime.now();
    }
}
