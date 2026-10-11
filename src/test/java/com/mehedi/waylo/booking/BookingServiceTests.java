package com.mehedi.waylo.booking;

import com.mehedi.waylo.entity.*;
import com.mehedi.waylo.enums.TripStatus;
import com.mehedi.waylo.groups.TripGroupService;
import com.mehedi.waylo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingServiceTests {
    final BookingRepository bookings = mock(BookingRepository.class);
    final PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    final TripRepository trips = mock(TripRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final TripGroupService groups = mock(TripGroupService.class);
    final BookingService service = new BookingService(bookings, attempts, trips, users, groups);
    final User traveler = new User();
    final Trip trip = new Trip();
    final Booking booking = new Booking();
    final PaymentAttempt attempt = new PaymentAttempt();
    LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Dhaka")); }
    BigDecimal money(String value) { return new BigDecimal(value); }

    @BeforeEach void setup() {
        traveler.setId(7L); traveler.setEnabled(true); traveler.setEmail("test@example.com"); traveler.setFullName("Test");
        User organizer = new User(); organizer.setId(8L);
        trip.setId(10L); trip.setOrganizer(organizer); trip.setStatus(TripStatus.PUBLISHED);
        trip.setDepartureDate(now().plusDays(2)); trip.setCapacity(3); trip.setPrice(money("1000.00"));
        booking.setId(11L); booking.setTrip(trip); booking.setTraveler(traveler); booking.setSeatCount(1);
        booking.setStatus("PENDING_PAYMENT"); booking.setHoldExpiresAt(now().plusMinutes(30));
        booking.setTripTotal(money("1000.00")); booking.setAdvanceAmount(money("100.00"));
        attempt.setBooking(booking); attempt.setTransactionId("Wtest123"); attempt.setStatus("INITIATED");
        attempt.setExpectedAmount(money("101.00")); attempt.setPaymentKind("ADVANCE");
        when(users.findByEmail("test@example.com")).thenReturn(Optional.of(traveler));
        when(trips.lockTripForBooking(10L)).thenReturn(Optional.of(trip));
        when(bookings.findTripIdForTraveler(11L, 7L)).thenReturn(Optional.of(10L));
        when(attempts.findTripIdByTransactionId("Wtest123")).thenReturn(Optional.of(10L));
        when(bookings.lockById(11L)).thenReturn(Optional.of(booking));
        when(bookings.findByIdAndTravelerId(11L, 7L)).thenReturn(Optional.of(booking));
        when(bookings.saveAndFlush(any())).thenAnswer(i -> { Booking b = i.getArgument(0); b.setId(11L); return b; });
        when(attempts.findByTransactionId("Wtest123")).thenReturn(Optional.of(attempt));
        when(attempts.lockByReference("Wtest123")).thenReturn(Optional.of(attempt));
    }
    SandboxPaymentGateway.Verification verified() {
        return new SandboxPaymentGateway.Verification("Wtest123", "val123", attempt.getExpectedAmount(), "bank123");
    }
    void balance() {
        booking.setStatus("CONFIRMED"); attempt.setPaymentKind("BALANCE"); attempt.setExpectedAmount(money("909.00"));
    }
    @Test void calculatesSeatsAdvanceAndOnlyCurrentOnePercentFee() {
        trip.setPrice(money("1234.56"));
        var start = service.reserve("test@example.com", 10L, 2, 25);
        assertEquals(money("623.45"), start.payableAmount());
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).saveAndFlush(saved.capture());
        assertEquals(money("2469.12"), saved.getValue().getTripTotal());
        assertEquals(money("617.28"), saved.getValue().getAdvanceAmount());
        assertEquals(money("6.17"), saved.getValue().getPlatformFee());
        assertEquals("PENDING_PAYMENT", saved.getValue().getStatus());
        verifyNoInteractions(groups);
    }
    @Test void fullAdvanceHasNoRemainingTripBalance() {
        service.reserve("test@example.com", 10L, 1, 100);
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).saveAndFlush(saved.capture());
        assertEquals(0, saved.getValue().remainingBalance().signum());
        assertEquals(money("1010.00"), saved.getValue().getPayableAmount());
    }
    @Test void invalidSeatsOrAdvanceCannotCreateReservation() {
        for (int seats : new int[]{0, 7}) assertThrows(IllegalArgumentException.class, () -> service.reserve("test@example.com",10L,seats,10));
        for (int percent : new int[]{9,101}) assertThrows(IllegalArgumentException.class, () -> service.reserve("test@example.com",10L,1,percent));
        verify(bookings, never()).saveAndFlush(any());
    }
    @Test void occupiedSeatsCannotBeOversold() {
        when(bookings.heldSeats(eq(10L), any())).thenReturn(3L);
        assertThrows(IllegalArgumentException.class, () -> service.reserve("test@example.com",10L,1,10));
    }
    @Test void duplicateActiveReservationIsRejected() {
        when(bookings.existingActive(eq(10L),eq(7L),any())).thenReturn(1L);
        assertThrows(IllegalArgumentException.class, () -> service.reserve("test@example.com",10L,1,10));
    }
    @Test void verifiedCallbackConfirmsAndEnrollsOnlyOnce() {
        service.applyVerified("Wtest123", verified());
        service.applyVerified("Wtest123", verified());
        assertEquals("CONFIRMED", booking.getStatus());
        assertEquals("VERIFIED", attempt.getStatus());
        verify(groups, times(1)).grantFromConfirmedBooking(booking);
    }
    @Test void latePaymentWithoutCapacityGoesToReview() {
        booking.setHoldExpiresAt(now().minusMinutes(1));
        when(bookings.heldSeats(eq(10L),any())).thenReturn(3L);
        service.applyVerified("Wtest123", verified());
        assertEquals("REVIEW", booking.getStatus()); assertEquals("REVIEW", attempt.getStatus());
        verifyNoInteractions(groups);
    }
    @Test void latePaymentCanConfirmIfCapacityStillExists() {
        booking.setHoldExpiresAt(now().minusMinutes(1));
        service.applyVerified("Wtest123", verified());
        assertEquals("CONFIRMED", booking.getStatus());
    }
    @Test void riskyPaymentCannotGrantAccess() {
        service.applyVerified("Wtest123", new SandboxPaymentGateway.Verification("Wtest123", "val123", money("101.00"), "bank123", "GATEWAY_RISK"));
        assertEquals("REVIEW", booking.getStatus()); assertEquals("GATEWAY_RISK", attempt.getReviewReason());
        verifyNoInteractions(groups);
    }
    @Test void wrongAmountIsReviewNotConfirmation() {
        service.applyVerified("Wtest123", new SandboxPaymentGateway.Verification("Wtest123", "val123", money("1.00"), "bank123"));
        assertEquals("AMOUNT_MISMATCH", attempt.getReviewReason()); verifyNoInteractions(groups);
    }
    @Test void cancelledTripCannotStartBalanceCheckout() {
        balance(); trip.setStatus(TripStatus.CANCELLED);
        assertThrows(IllegalArgumentException.class, () -> service.reserveBalance("test@example.com",11L));
        verify(attempts, never()).saveAndFlush(any());
    }
    @Test void currentBalanceFeeIsOnePercent() {
        balance();
        assertEquals(money("909.00"), service.reserveBalance("test@example.com",11L).payableAmount());
    }
    @Test void duplicateOpenBalanceCheckoutIsRejected() {
        balance(); when(attempts.existsByBookingIdAndPaymentKindAndStatusAndCreatedAtAfter(eq(11L),eq("BALANCE"),eq("INITIATED"),any())).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.reserveBalance("test@example.com",11L));
    }
    @Test void balanceCallbackIsIdempotent() {
        balance(); service.applyVerified("Wtest123",verified()); service.applyVerified("Wtest123",verified());
        assertEquals(money("900.00"), booking.getBalancePaidAmount()); assertEquals("VERIFIED", attempt.getStatus());
    }
    @Test void cancelledTripLateBalancePreservesPaymentForReview() {
        balance(); trip.setStatus(TripStatus.CANCELLED); service.applyVerified("Wtest123",verified());
        assertEquals("REVIEW", attempt.getStatus()); assertEquals(0,booking.getBalancePaidAmount().signum());
        assertEquals("CONFIRMED", booking.getStatus());
    }
    @Test void verifiedFailureReleasesHold() {
        service.applyTerminal("Wtest123","FAILED"); assertEquals("FAILED",booking.getStatus());
        assertEquals("FAILED",attempt.getStatus());
    }
    @Test void verifiedCancellationReleasesHold() {
        service.applyTerminal("Wtest123","CANCELLED"); assertEquals("CANCELLED",booking.getStatus());
    }
    @Test void failureCannotUndoConfirmedPayment() {
        service.applyVerified("Wtest123",verified()); service.applyTerminal("Wtest123","FAILED");
        assertEquals("CONFIRMED",booking.getStatus()); assertEquals("VERIFIED",attempt.getStatus());
    }
    @Test void failedBalanceDoesNotCancelOriginalBooking() {
        balance(); service.applyTerminal("Wtest123","FAILED"); assertEquals("CONFIRMED",booking.getStatus());
        assertEquals(0,booking.getBalancePaidAmount().signum());
    }
    @Test void gatewayInitializationFailureReleasesReservation() {
        service.releaseIfGatewayFails("Wtest123"); assertEquals("FAILED", booking.getStatus());
    }
}
