package com.mehedi.waylo.service;

import com.mehedi.waylo.dto.TripRequest;
import com.mehedi.waylo.entity.Destination;
import com.mehedi.waylo.entity.Trip;
import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.enums.Role;
import com.mehedi.waylo.enums.TripStatus;
import com.mehedi.waylo.repository.DestinationRepository;
import com.mehedi.waylo.repository.TripRepository;
import com.mehedi.waylo.repository.UserRepository;
import com.mehedi.waylo.tripscope.TripProposalService;
import jakarta.validation.Validator;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class OrganizerTripService {
    private final TripRepository trips;
    private final DestinationRepository destinations;
    private final UserRepository users;
    private final Validator validator;
    private final MediaStorageService media;
    private final TripProposalService proposals;
    private final AuditService audit;

    public OrganizerTripService(TripRepository trips, DestinationRepository destinations,
                                UserRepository users, Validator validator,
                                MediaStorageService media, TripProposalService proposals, AuditService audit) {
        this.trips = trips;
        this.destinations = destinations;
        this.users = users;
        this.validator = validator;
        this.media = media;
        this.proposals = proposals;
        this.audit = audit;
    }

    private User current(String email) {
        if (email == null) throw new AccessDeniedException("Sign in first.");
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(User::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable."));
    }

    private boolean globalManager(User user) {
        return user.getRole() == Role.ORGANIZER || user.getRole() == Role.ADMIN;
    }

    /** Creating additional trips is deliberately reserved for global managers. */
    public void requireGlobalManager(String email) {
        if (!globalManager(current(email)))
            throw new AccessDeniedException("For a single trip, apply for trip-specific approval first.");
    }

    private void checkScopedPermission(User user, Long tripId) {
        if (!globalManager(user) && !proposals.approvedForTrip(user.getId(), tripId))
            throw new AccessDeniedException("You can only manage your individually approved trip.");
    }

    @Transactional(readOnly = true)
    public List<Trip> myTrips(String email) {
        User user = current(email);
        List<Trip> ownTrips = trips.findByOrganizerIdOrderByIdDesc(user.getId());
        if (globalManager(user)) return ownTrips;
        return ownTrips.stream().filter(t -> proposals.approvedForTrip(user.getId(), t.getId())).toList();
    }

    @Transactional(readOnly = true)
    public List<Destination> destinations() {
        return destinations.findAll(Sort.by("name"));
    }

    private Trip owned(String email, Long id) {
        User user = current(email);
        Trip trip = trips.findOwnedForUpdate(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found."));
        checkScopedPermission(user, id);
        return trip;
    }

    private void draft(Trip trip) {
        if (trip.getStatus() != TripStatus.DRAFT)
            throw new IllegalArgumentException("Only drafts can be edited. Published trips are locked.");
    }

    private void validate(TripRequest request) {
        if (!validator.validate(request).isEmpty())
            throw new IllegalArgumentException("Check the trip details and try again.");
        if (!request.getDepartureDate().isAfter(LocalDateTime.now(ZoneId.of("Asia/Dhaka"))))
            throw new IllegalArgumentException("Departure must be in the future (Bangladesh time).");
    }

    private void apply(Trip trip, TripRequest request) {
        validate(request);
        Destination destination = destinations.findById(request.getDestinationId())
                .orElseThrow(() -> new IllegalArgumentException("Select an available destination."));
        trip.setTitle(request.getTitle().trim());
        trip.setDescription(request.getDescription().trim());
        trip.setDestination(destination);
        trip.setDepartureDate(request.getDepartureDate());
        trip.setPrice(request.getPrice());
        trip.setCapacity(request.getCapacity());
    }

    public Long create(String email, TripRequest request, MultipartFile cover) {
        User manager = current(email);
        if (!globalManager(manager))
            throw new AccessDeniedException("Only global organizers can create additional trips.");
        Trip trip = new Trip();
        trip.setOrganizer(manager);
        apply(trip, request);
        trip.setStatus(TripStatus.DRAFT);
        MediaStorageService.StoredPhoto uploaded = media.store(cover, MediaStorageService.PhotoKind.TRIP);
        if (uploaded != null) {
            trip.setCoverImageUrl(uploaded.url());
            trip.setCoverImagePublicId(uploaded.publicId());
        }
        return trips.saveAndFlush(trip).getId();
    }

    public TripRequest editForm(String email, Long id) {
        Trip trip = owned(email, id);
        draft(trip);
        return requestFor(trip);
    }

    private TripRequest requestFor(Trip trip) {
        TripRequest request = new TripRequest();
        request.setTitle(trip.getTitle());
        request.setDescription(trip.getDescription());
        request.setDestinationId(trip.getDestination().getId());
        request.setDepartureDate(trip.getDepartureDate());
        request.setPrice(trip.getPrice());
        request.setCapacity(trip.getCapacity());
        return request;
    }

    public void update(String email, Long id, TripRequest request, MultipartFile cover) {
        Trip trip = owned(email, id);
        draft(trip);
        apply(trip, request);
        MediaStorageService.StoredPhoto uploaded = media.store(cover, MediaStorageService.PhotoKind.TRIP);
        String oldId = trip.getCoverImagePublicId();
        String oldUrl = trip.getCoverImageUrl();
        if (uploaded != null) {
            trip.setCoverImageUrl(uploaded.url());
            trip.setCoverImagePublicId(uploaded.publicId());
        }
        trips.saveAndFlush(trip);
        if (uploaded != null) media.deleteAfterCommit(oldId, oldUrl);
    }

    /** Replace just the photo on an owned draft or published trip.
     * This does not unlock published trip details or grant additional trip access.
     */
    public void replaceCover(String email, Long id, MultipartFile coverPhoto) {
        Trip trip = owned(email, id);
        if (trip.getStatus() != TripStatus.DRAFT && trip.getStatus() != TripStatus.PUBLISHED) {
            throw new IllegalArgumentException("Only draft or published trips can change their cover.");
        }
        if (coverPhoto == null || coverPhoto.isEmpty()) {
            throw new IllegalArgumentException("Choose a JPEG or PNG cover photo first.");
        }

        // MediaStorageService checks file size/content, uploads to Cloudinary,
        // and cleans up newly uploaded assets if the DB transaction rolls back.
        MediaStorageService.StoredPhoto photo = media.store(
                coverPhoto, MediaStorageService.PhotoKind.TRIP);
        String oldPublicId = trip.getCoverImagePublicId();
        String oldUrl = trip.getCoverImageUrl();

        trip.setCoverImageUrl(photo.url());
        trip.setCoverImagePublicId(photo.publicId());
        trips.saveAndFlush(trip);
        media.deleteAfterCommit(oldPublicId, oldUrl);
    }

    @Transactional(readOnly = true)
    public String coverForEdit(String email, Long id) {
        User user = current(email);
        Trip trip = trips.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!trip.getOrganizer().getId().equals(user.getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        checkScopedPermission(user, id);
        draft(trip);
        return trip.getCoverImageUrl();
    }

    public void publish(String email, Long id) {
        Trip trip = owned(email, id);
        if (trip.getStatus() == TripStatus.PUBLISHED) return;
        draft(trip);
        validate(requestFor(trip));
        trip.setStatus(TripStatus.PUBLISHED);
        trips.save(trip);
        audit.record(current(email).getId(),"TRIP_PUBLISHED","TRIP",id,"Organizer published validated draft");
    }
}
