package com.mehedi.waylo.booking;

import com.mehedi.waylo.entity.Trip;
import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.enums.TripStatus;
import com.mehedi.waylo.groups.TripGroupService;
import com.mehedi.waylo.repository.TripRepository;
import com.mehedi.waylo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class BookingService {

    // MySQL READ_COMMITTED plus trip-first locking keeps capacity queries current
    // after waiting for another checkout. Scalar lookups avoid caching stale entities
    // in Hibernate before their pessimistic locks have been acquired.

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private final BookingRepository bookings;
    private final PaymentAttemptRepository attempts;
    private final TripRepository trips;
    private final UserRepository users;
    private final TripGroupService groups;

    public BookingService(BookingRepository bookings, PaymentAttemptRepository attempts,
                          TripRepository trips, UserRepository users, TripGroupService groups) {
        this.bookings = bookings;
        this.attempts = attempts;
        this.trips = trips;
        this.users = users;
        this.groups = groups;
    }

    private User user(String email) {
        if (email == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(User::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private LocalDateTime now() { return LocalDateTime.now(DHAKA); }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void expireHold(Long id) {
        Long tripId = bookings.tripIdForBooking(id).orElse(null);
        if (tripId == null) return;
        trips.lockTripForBooking(tripId).orElseThrow();
        Booking booking = bookings.lockById(id).orElseThrow();
        if ("PENDING_PAYMENT".equals(booking.getStatus()) && !booking.getHoldExpiresAt().isAfter(now()))
            booking.setStatus("EXPIRED");
        // A late verified payment is still reconciled against fresh capacity.
    }

    @Transactional(readOnly = true)
    public int availableSeats(Trip trip) {
        long held = bookings.heldSeats(trip.getId(), now());
        return (int) Math.max(0, trip.getCapacity() - held);
    }

    public record Start(Long bookingId, String transactionId, BigDecimal payableAmount,
                        String travelerName, String travelerEmail) { }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Start reserve(String email, Long tripId, int seats, int advancePercent) {
        if (seats < 1 || seats > 6) throw new IllegalArgumentException("Choose between 1 and 6 seats.");
        if (advancePercent < 10 || advancePercent > 100)
            throw new IllegalArgumentException("Advance payment must be between 10% and 100%.");

        User traveler = user(email);
        // Locking the trip serializes capacity checks for simultaneous customers.
        Trip trip = trips.lockTripForBooking(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (trip.getStatus() != TripStatus.PUBLISHED
                || !trip.getDepartureDate().isAfter(now()))
            throw new IllegalArgumentException("This trip is no longer bookable.");
        if (trip.getOrganizer().getId().equals(traveler.getId()))
            throw new IllegalArgumentException("Organizers cannot book their own trips.");
        if (bookings.existingActive(tripId, traveler.getId(), now()) > 0)
            throw new IllegalArgumentException("You already have an active reservation for this trip. Check My Bookings.");
        if (availableSeats(trip) < seats)
            throw new IllegalArgumentException("Not enough seats remain.");

        BigDecimal total = trip.getPrice().multiply(BigDecimal.valueOf(seats)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal advance = total.multiply(BigDecimal.valueOf(advancePercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal fee = advance.multiply(new BigDecimal("0.01"))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal due = advance.add(fee);
        if (due.compareTo(new BigDecimal("10.00")) < 0 || due.compareTo(new BigDecimal("500000.00")) > 0)
            throw new IllegalArgumentException("Sandbox checkout requires a payment between ৳10 and ৳500,000.");

        Booking booking = new Booking();
        booking.setTrip(trip);
        booking.setTraveler(traveler);
        booking.setSeatCount(seats);
        booking.setPricePerSeat(trip.getPrice());
        booking.setTripTotal(total);
        booking.setAdvancePercent(advancePercent);
        booking.setAdvanceAmount(advance);
        booking.setPlatformFee(fee);
        booking.setPayableAmount(due);
        booking.setStatus("PENDING_PAYMENT");
        booking.setCreatedAt(now());
        booking.setHoldExpiresAt(now().plusMinutes(30));
        booking = bookings.saveAndFlush(booking);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setBooking(booking);
        attempt.setTransactionId("W" + UUID.randomUUID().toString().replace("-", "").substring(0, 25));
        attempt.setExpectedAmount(due);
        attempt.setPaymentKind("ADVANCE");
        attempt.setStatus("INITIATED");
        attempt.setCreatedAt(now());
        attempts.save(attempt);

        return new Start(booking.getId(), attempt.getTransactionId(), due,
                traveler.getFullName(), traveler.getEmail());
    }

    /** A confirmed booking can pay its remaining trip balance through sandbox checkout.
     *  Another 1% Waylo fee is applied to this second payment only.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Start reserveBalance(String email, Long bookingId) {
        User traveler = user(email);
        Long tripId = bookings.findTripIdForTraveler(bookingId, traveler.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Trip trip = trips.lockTripForBooking(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Booking booking = bookings.lockById(bookingId).orElseThrow();
        if (!booking.getTraveler().getId().equals(traveler.getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!"CONFIRMED".equals(booking.getStatus()) || trip.getStatus() != TripStatus.PUBLISHED
                || !trip.getDepartureDate().isAfter(now()))
            throw new IllegalArgumentException("Balance payments require a confirmed booking on a published, upcoming trip.");
        BigDecimal balance = booking.remainingBalance();
        if (balance.signum() <= 0)
            throw new IllegalArgumentException("Your trip is already fully paid.");
        if (attempts.existsByBookingIdAndPaymentKindAndStatusAndCreatedAtAfter(
                bookingId, "BALANCE", "INITIATED", now().minusMinutes(30)))
            throw new IllegalArgumentException("A balance checkout is already open. Wait 30 minutes before starting another.");
        BigDecimal fee = balance.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal payable = balance.add(fee);
        if (payable.compareTo(new BigDecimal("10.00")) < 0
                || payable.compareTo(new BigDecimal("500000.00")) > 0)
            throw new IllegalArgumentException("Sandbox balance checkout must be between ৳10 and ৳500,000.");
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setBooking(booking);
        attempt.setTransactionId("W" + UUID.randomUUID().toString().replace("-", "").substring(0, 25));
        attempt.setPaymentKind("BALANCE");
        attempt.setExpectedAmount(payable);
        attempt.setStatus("INITIATED");
        attempt.setCreatedAt(now());
        attempts.saveAndFlush(attempt);
        return new Start(booking.getId(), attempt.getTransactionId(), payable,
                traveler.getFullName(), traveler.getEmail());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void releaseIfGatewayFails(String transactionId) {
        Long tripId = attempts.findTripIdByTransactionId(transactionId).orElse(null);
        if (tripId == null) return;
        Trip trip = trips.lockTripForBooking(tripId).orElse(null);
        if (trip == null) return;
        PaymentAttempt locked = attempts.lockByReference(transactionId).orElse(null);
        if (locked == null || !"INITIATED".equals(locked.getStatus())) return;
        Booking booking = bookings.lockById(locked.getBooking().getId()).orElseThrow();
        if ("BALANCE".equals(locked.getPaymentKind())) {
            locked.setStatus("FAILED");
        } else if ("PENDING_PAYMENT".equals(booking.getStatus())) {
            booking.setStatus("FAILED");
            locked.setStatus("FAILED");
        }
    }

    @Transactional(readOnly = true)
    public List<Booking> myBookings(String email) {
        return bookings.findByTravelerIdOrderByCreatedAtDesc(user(email).getId());
    }

    @Transactional(readOnly = true)
    public Booking mine(String email, Long bookingId) {
        return bookings.findByIdAndTravelerId(bookingId, user(email).getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void applyVerified(String transactionId, SandboxPaymentGateway.Verification verified) {
        Long tripId = attempts.findTripIdByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment reference."));
        Trip trip = trips.lockTripForBooking(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip unavailable."));
        PaymentAttempt attempt = attempts.lockByReference(transactionId).orElseThrow();
        Booking booking = bookings.lockById(attempt.getBooking().getId()).orElseThrow();

        if ("VERIFIED".equals(attempt.getStatus()) || "REVIEW".equals(attempt.getStatus())) return;
        // Duplicate IPN and success callbacks never charge or grant twice.
        if (!transactionId.equals(verified.transactionId()))
            throw new IllegalArgumentException("Payment details do not match this booking.");
        if (verified.reviewReason() != null || verified.amount().compareTo(attempt.getExpectedAmount()) != 0) {
            attempt.setStatus("REVIEW");
            attempt.setReviewReason(verified.reviewReason() == null ? "AMOUNT_MISMATCH" : verified.reviewReason());
            attempt.setGatewayValidationId(verified.validationId());
            attempt.setBankTransactionId(verified.bankTransactionId());
            attempt.setVerifiedAt(now());
            if (!"BALANCE".equals(attempt.getPaymentKind())) booking.setStatus("REVIEW");
            return;
        }

        if ("BALANCE".equals(attempt.getPaymentKind())) {
            // Every independent sandbox payment gets a separate 1% fee.
            // A second simultaneous/late payment is flagged for human reconciliation.
            BigDecimal balance = booking.remainingBalance();
            BigDecimal expected = balance.add(balance.multiply(new BigDecimal("0.01"))
                    .setScale(2, RoundingMode.HALF_UP));
            if ("CONFIRMED".equals(booking.getStatus())
                    && trip.getStatus() == TripStatus.PUBLISHED
                    && trip.getDepartureDate().isAfter(now())
                    && balance.signum() > 0 && expected.compareTo(verified.amount()) == 0) {
                booking.setBalancePaidAmount(booking.getTripTotal().subtract(booking.getAdvanceAmount()));
                attempt.setStatus("VERIFIED");
            } else {
                attempt.setStatus("REVIEW");
                attempt.setReviewReason("BALANCE_NO_LONGER_PAYABLE");
            }
            attempt.setGatewayValidationId(verified.validationId());
            attempt.setBankTransactionId(verified.bankTransactionId());
            attempt.setVerifiedAt(now());
            return;
        }

        attempt.setStatus("VERIFIED");
        attempt.setGatewayValidationId(verified.validationId());
        attempt.setBankTransactionId(verified.bankTransactionId());
        attempt.setVerifiedAt(now());

        boolean holdActive = "PENDING_PAYMENT".equals(booking.getStatus())
                && booking.getHoldExpiresAt().isAfter(now());
        // Late callbacks must not confirm a second active booking for the same traveler.
        boolean alreadyActiveElsewhere = bookings.existingActive(
                trip.getId(), booking.getTraveler().getId(), now()) > (holdActive ? 1 : 0);
        boolean hasFreshCapacity = availableSeats(trip) >= booking.getSeatCount();
        if (trip.getStatus() == TripStatus.PUBLISHED
                && trip.getDepartureDate().isAfter(now())
                && !alreadyActiveElsewhere
                && (holdActive || hasFreshCapacity)) {
            booking.setStatus("CONFIRMED");
            booking.setConfirmedAt(now());
            // Enroll only after server-to-server payment verification.
            groups.grantFromConfirmedBooking(booking);
        } else {
            // Never oversell a late payment. Manual reconciliation/refund may be required.
            booking.setStatus("REVIEW");
            attempt.setStatus("REVIEW");
            attempt.setReviewReason("TRIP_OR_CAPACITY_UNAVAILABLE");
        }
    }

    /** Only called with a status obtained from the authenticated gateway query. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void applyTerminal(String reference, String status) {
        if (!"FAILED".equals(status) && !"CANCELLED".equals(status))
            throw new IllegalArgumentException("Not a terminal gateway status.");
        Long tripId = attempts.findTripIdByTransactionId(reference).orElseThrow();
        trips.lockTripForBooking(tripId).orElseThrow();
        PaymentAttempt attempt = attempts.lockByReference(reference).orElseThrow();
        Booking booking = bookings.lockById(attempt.getBooking().getId()).orElseThrow();
        if (!"INITIATED".equals(attempt.getStatus())) return;
        attempt.setStatus(status);
        if ("ADVANCE".equals(attempt.getPaymentKind()) && "PENDING_PAYMENT".equals(booking.getStatus()))
            booking.setStatus(status);
    }
}
