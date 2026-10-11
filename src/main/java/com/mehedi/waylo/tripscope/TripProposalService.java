package com.mehedi.waylo.tripscope;

import com.mehedi.waylo.dto.DestinationRequest;
import com.mehedi.waylo.entity.Destination;
import com.mehedi.waylo.entity.Trip;
import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.enums.Role;
import com.mehedi.waylo.enums.TripStatus;
import com.mehedi.waylo.repository.DestinationRepository;
import com.mehedi.waylo.repository.TripRepository;
import com.mehedi.waylo.repository.UserRepository;
import com.mehedi.waylo.service.DestinationService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
public class TripProposalService {
    private final TripProposalRepository proposals;
    private final UserRepository users;
    private final DestinationRepository destinations;
    private final TripRepository trips;
    private final DestinationService destinationService;
    private final com.mehedi.waylo.service.AuditService audit;
    private final com.mehedi.waylo.service.NotificationService notifications;

    public TripProposalService(TripProposalRepository proposals, UserRepository users,
                               DestinationRepository destinations, TripRepository trips,
                               DestinationService destinationService, com.mehedi.waylo.service.AuditService audit, com.mehedi.waylo.service.NotificationService notifications) {
        this.proposals = proposals;
        this.users = users;
        this.destinations = destinations;
        this.trips = trips;
        this.destinationService = destinationService;
        this.audit=audit; this.notifications=notifications;
    }

    private User actor(String email) {
        if (email == null) throw new AccessDeniedException("Sign in first.");
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(User::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable."));
    }

    @Transactional(readOnly = true)
    public List<Destination> destinations() {
        return destinations.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public List<TripProposal> mine(String email) {
        return proposals.findByApplicantIdOrderByAppliedAtDesc(actor(email).getId());
    }

    @Transactional(readOnly = true)
    public boolean hasApprovedTrip(String email) {
        if (email == null || email.isBlank()) return false;
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(User::isEnabled)
                .map(u -> proposals.existsByApplicantIdAndStatusAndApprovedTripIsNotNull(
                        u.getId(), TripProposalStatus.APPROVED)).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean approvedForTrip(Long userId, Long tripId) {
        return proposals.existsByApplicantIdAndApprovedTripIdAndStatus(
                userId, tripId, TripProposalStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public List<TripProposal> pending() {
        return proposals.findByStatusOrderByAppliedAtAsc(TripProposalStatus.PENDING);
    }

    @Transactional
    public void submit(String email, TripProposalRequest request) {
        User applicant = actor(email);
        if (proposals.existsByApplicantIdAndStatus(applicant.getId(), TripProposalStatus.PENDING))
            throw new IllegalArgumentException("Your earlier trip proposal is awaiting review.");
        if (request.getTitle() == null || request.getTitle().trim().length() < 8
                || request.getDescription() == null || request.getDescription().trim().length() < 30
                || request.getMotivation() == null || request.getMotivation().trim().length() < 30
                || request.getDescription().length() > 5000 || request.getMotivation().length() > 1500
                || request.getTitle().length() > 200 || request.getDocumentType() == null
                || request.getPrice() == null || request.getPrice().signum() <= 0
                || request.getPrice().compareTo(new java.math.BigDecimal("99999999.99")) > 0
                || request.getCapacity() < 1 || request.getCapacity() > 1000)
            throw new IllegalArgumentException("Check the proposed trip information.");
        if (request.getDepartureDate() == null || !request.getDepartureDate().isAfter(
                LocalDateTime.now(ZoneId.of("Asia/Dhaka")).plusHours(24)))
            throw new IllegalArgumentException("Proposed departure must be more than 24 hours away (Bangladesh time).");
        // Exactly one route: an existing MySQL destination or a suggested new location.
        // New locations are NOT made public by a member; admins resolve them on approval.
        String suggestedName = clean(request.getSuggestedDestinationName());
        boolean suggestingNew = !suggestedName.isEmpty();
        if (suggestingNew == (request.getDestinationId() != null))
            throw new IllegalArgumentException(
                    "Choose an existing destination OR enter a new destination name.");
        Destination place = null;
        if (!suggestingNew) {
            place = destinations.findById(request.getDestinationId())
                    .orElseThrow(() -> new IllegalArgumentException("Select an available destination."));
        } else {
            validateNewDestination(request);
        }

        TripProposal proposal = new TripProposal();
        proposal.setApplicant(applicant);
        proposal.setDestination(place);
        if (suggestingNew) {
            proposal.setSuggestedDestinationName(suggestedName);
            proposal.setSuggestedDistrict(clean(request.getSuggestedDistrict()));
            proposal.setSuggestedCountry(clean(request.getSuggestedCountry()));
            proposal.setSuggestedDescription(clean(request.getSuggestedDescription()));
            proposal.setSuggestedLatitude(request.getSuggestedLatitude());
            proposal.setSuggestedLongitude(request.getSuggestedLongitude());
        }
        proposal.setTitle(request.getTitle().trim());
        proposal.setDescription(request.getDescription().trim());
        proposal.setDepartureDate(request.getDepartureDate());
        proposal.setPrice(request.getPrice());
        proposal.setCapacity(request.getCapacity());
        proposal.setMotivation(request.getMotivation().trim());
        proposal.setDocumentType(request.getDocumentType());
        proposal.setStatus(TripProposalStatus.PENDING);
        proposals.save(proposal);
    }


    private String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private void validateNewDestination(TripProposalRequest request) {
        String name = clean(request.getSuggestedDestinationName());
        String district = clean(request.getSuggestedDistrict());
        String country = clean(request.getSuggestedCountry());
        if (name.isEmpty() || district.isEmpty() || country.isEmpty())
            throw new IllegalArgumentException("Enter a new destination name, district and country.");
        if (name.length() > 150 || district.length() > 120 || country.length() > 120)
            throw new IllegalArgumentException("Destination name, district or country is too long.");
        if (clean(request.getSuggestedDescription()).length() > 1000)
            throw new IllegalArgumentException("Destination description is too long.");
        var lat = request.getSuggestedLatitude();
        var lon = request.getSuggestedLongitude();
        if ((lat == null) != (lon == null))
            throw new IllegalArgumentException("Provide both coordinates or leave both blank.");
        if (lat != null && (lat.compareTo(new java.math.BigDecimal("-90")) < 0
                || lat.compareTo(new java.math.BigDecimal("90")) > 0
                || lon.compareTo(new java.math.BigDecimal("-180")) < 0
                || lon.compareTo(new java.math.BigDecimal("180")) > 0))
            throw new IllegalArgumentException("Coordinates are outside the valid range.");
    }

    private Destination resolveApprovedDestination(TripProposal proposal, String adminEmail) {
        if (proposal.getDestination() != null) return proposal.getDestination();
        String name = clean(proposal.getSuggestedDestinationName());
        String district = clean(proposal.getSuggestedDistrict());
        String country = clean(proposal.getSuggestedCountry());
        if (name.isEmpty() || district.isEmpty() || country.isEmpty())
            throw new IllegalArgumentException("This proposal has no valid destination.");
        for (Destination existing : destinations.findAll()) {
            String oldDistrict = clean(existing.getDistrict() == null
                    ? existing.getLocation() : existing.getDistrict());
            String oldCountry = clean(existing.getCountry() == null
                    ? "Bangladesh" : existing.getCountry());
            if (existing.getName().equalsIgnoreCase(name)
                    && oldDistrict.equalsIgnoreCase(district)
                    && oldCountry.equalsIgnoreCase(country)) return existing;
        }
        DestinationRequest request = new DestinationRequest();
        request.setName(name);
        request.setDistrict(district);
        request.setCountry(country);
        request.setDescription(proposal.getSuggestedDescription());
        request.setLatitude(proposal.getSuggestedLatitude());
        request.setLongitude(proposal.getSuggestedLongitude());
        // DestinationService restricts creation to Admins and approved Trip Managers,
        // validates duplicates, and evicts the public destination cache.
        return destinationService.add(adminEmail, request);
    }

    /** Approval creates one owned draft; MEMBER role remains unchanged. */
    @Transactional
    public Long review(Long id, String adminEmail, boolean approve, String reason, boolean reviewed) {
        User admin = actor(adminEmail);
        if (admin.getRole() != Role.ADMIN) throw new AccessDeniedException("Admin access required.");
        TripProposal proposal = proposals.lockForReview(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (proposal.getStatus() != TripProposalStatus.PENDING)
            throw new IllegalArgumentException("This proposal has already been reviewed.");
        if (!proposal.getApplicant().isEnabled()) throw new IllegalArgumentException("Applicant account is disabled.");
        if (approve && !reviewed)
            throw new IllegalArgumentException("Confirm that you manually reviewed the trip details before approval.");
        if (!approve && (reason == null || reason.trim().length() < 10 || reason.length() > 500))
            throw new IllegalArgumentException("Please explain the rejection (10–500 characters).");
        proposal.setReviewedBy(admin);
        proposal.setReviewedAt(LocalDateTime.now());
        if (approve) {
            if (!proposal.getDepartureDate().isAfter(LocalDateTime.now(ZoneId.of("Asia/Dhaka"))))
                throw new IllegalArgumentException("The proposed departure date has passed.");
            // Create the requested place only as part of an admin-approved proposal.
            // Reuse an existing normalized match to avoid adding duplicate destinations.
            Destination chosen = resolveApprovedDestination(proposal, adminEmail);
            proposal.setDestination(chosen);
            Trip trip = new Trip();
            trip.setOrganizer(proposal.getApplicant());
            trip.setDestination(chosen);
            trip.setTitle(proposal.getTitle());
            trip.setDescription(proposal.getDescription());
            trip.setPrice(proposal.getPrice());
            trip.setCapacity(proposal.getCapacity());
            trip.setDepartureDate(proposal.getDepartureDate());
            trip.setStatus(TripStatus.DRAFT);
            trip = trips.saveAndFlush(trip);
            proposal.setApprovedTrip(trip);
            proposal.setStatus(TripProposalStatus.APPROVED);
            audit.record(admin.getId(),"TRIP_PROPOSAL_APPROVED","PROPOSAL",id,"Approved one trip; global role unchanged");
            notifications.send(proposal.getApplicant().getId(),"Your single-trip proposal was approved. You can manage this trip; your global account role is unchanged.","/organizer/trips");
            return trip.getId();
        }
        proposal.setStatus(TripProposalStatus.REJECTED);
        proposal.setRejectionReason(reason.trim());
        audit.record(admin.getId(),"TRIP_PROPOSAL_REJECTED","PROPOSAL",id,reason.trim());
        notifications.send(proposal.getApplicant().getId(),"Your trip proposal was rejected. Open My Proposals to see the review reason.","/trip-proposals");
        return null;
    }
}
