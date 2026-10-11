package com.mehedi.waylo.booking;

import com.mehedi.waylo.groups.TripGroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingCallbackTests {
    final BookingService bookings = mock(BookingService.class);
    final PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    final SandboxPaymentGateway gateway = mock(SandboxPaymentGateway.class);
    final BookingController controller = new BookingController(bookings, attempts, gateway, mock(TripGroupService.class));
    final PaymentAttempt attempt = new PaymentAttempt();
    @BeforeEach void setup() {
        attempt.setStatus("INITIATED"); attempt.setExpectedAmount(new BigDecimal("101.00"));
        when(gateway.ready()).thenReturn(true);
        when(attempts.findByTransactionId("Wtest123")).thenReturn(Optional.of(attempt));
    }
    @Test void missingOrUnknownReferenceIsNotAcknowledgedAsSuccess() {
        assertEquals(400, controller.ipn(null,null).getStatusCode().value());
        assertEquals(400, controller.ipn("Wunknown", "val123").getStatusCode().value());
        verifyNoInteractions(bookings);
    }
    @Test void callbackMustValidateWithServerExpectedAmount() {
        var verified = new SandboxPaymentGateway.Verification("Wtest123","val123",new BigDecimal("101.00"),"bank123");
        when(gateway.notification("Wtest123","val123",new BigDecimal("101.00")))
                .thenReturn(new SandboxPaymentGateway.Notification(verified,null));
        assertEquals(200,controller.ipn("Wtest123","val123").getStatusCode().value());
        verify(bookings).applyVerified("Wtest123",verified);
    }
    @Test void outageReturnsRetryAndDoesNotChangeBooking() {
        when(gateway.notification(anyString(),anyString(),any())).thenThrow(new IllegalStateException("unavailable"));
        assertEquals(503,controller.ipn("Wtest123","val123").getStatusCode().value());
        verifyNoInteractions(bookings);
    }
    @Test void invalidReceiptReturnsBadRequest() {
        when(gateway.notification(anyString(),anyString(),any())).thenThrow(new IllegalArgumentException("mismatch"));
        assertEquals(400,controller.ipn("Wtest123","val123").getStatusCode().value());
        verifyNoInteractions(bookings);
    }
    @Test void duplicateCallbackAcknowledgesWithoutSecondMutation() {
        attempt.setStatus("VERIFIED");
        assertEquals(200,controller.ipn("Wtest123","val123").getStatusCode().value());
        verify(gateway,never()).notification(any(),any(),any()); verifyNoInteractions(bookings);
    }
    @Test void disabledCheckoutDoesNotReserveAnything() {
        when(gateway.ready()).thenReturn(false);
        controller.checkout(1L,1,10,"phone","address","city","postcode",null,new RedirectAttributesModelMap());
        verifyNoInteractions(bookings);
        assertEquals(503,controller.ipn("Wtest123","val123").getStatusCode().value());
    }
    @Test void browserCancellationAloneCannotReleaseReservation() {
        when(gateway.notification(eq("Wtest123"),isNull(),any())).thenThrow(new IllegalStateException("pending"));
        assertEquals("booking/gateway-return",controller.failed("Wtest123",new ExtendedModelMap()));
        verifyNoInteractions(bookings);
    }
    @Test void verifiedCancellationUpdatesBooking() {
        when(gateway.notification(eq("Wtest123"),isNull(),any()))
                .thenReturn(new SandboxPaymentGateway.Notification(null,"CANCELLED"));
        controller.failed("Wtest123",new ExtendedModelMap());
        verify(bookings).applyTerminal("Wtest123","CANCELLED");
    }

    @Test void forgedHttpStatusAndAmountCannotConfirmBooking() throws Exception {
        when(gateway.notification(eq("Wtest123"),eq("val123"),eq(new BigDecimal("101.00"))))
                .thenThrow(new IllegalArgumentException("gateway rejected"));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/payments/sslcommerz/ipn").param("tran_id","Wtest123")
                .param("val_id","val123").param("status","VALID").param("amount","0.01"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        verifyNoInteractions(bookings);
    }
}
