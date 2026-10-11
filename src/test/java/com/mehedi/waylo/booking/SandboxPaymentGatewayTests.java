package com.mehedi.waylo.booking;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.hamcrest.Matchers.containsString;

class SandboxPaymentGatewayTests {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private SandboxPaymentGateway gateway(boolean enabled, String url) {
        return new SandboxPaymentGateway(builder.build(), enabled, "fake-store", "fake-password", url);
    }
    private SandboxPaymentGateway gateway() { return gateway(true, "https://waylo.example.com"); }
    private String receipt(String extra) {
        return "{\"status\":\"VALID\",\"tran_id\":\"Wtest123\",\"val_id\":\"val123\","
                + "\"bank_tran_id\":\"bank123\",\"amount\":\"101.00\",\"currency\":\"BDT\",\"risk_level\":\"0\"" + extra + "}";
    }
    private void response(String json) {
        server.expect(requestTo(containsString("https://sandbox.sslcommerz.com/validator/api/validationserverAPI.php?")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
    private SandboxPaymentGateway.Verification verify() {
        return gateway().verify("Wtest123", "val123", new BigDecimal("101.00"));
    }

    @Test void disabledNeedsNoCredentialsAndMakesNoNetworkCall() {
        var disabled = new SandboxPaymentGateway(builder.build(), false, "", "", "http://localhost:8080");
        assertFalse(disabled.ready());
        assertThrows(IllegalStateException.class, () -> disabled.verify("Wtest123", "val123", BigDecimal.TEN));
        server.verify();
    }
    @Test void rejectsUnsafeCallbackOrigins() {
        for (String url : new String[]{"http://waylo.example.com", "https://localhost", "https://127.0.0.1",
                "https://waylo.example.com?x=y", "https://waylo.example.com/path", "https://u@waylo.example.com"})
            assertFalse(gateway(true, url).ready(), url);
        assertTrue(gateway().ready());
    }
    @Test void validPaymentIsAccepted() {
        response(receipt(""));
        assertNull(verify().reviewReason());
        server.verify();
    }
    @Test void alreadyValidatedPaymentIsAcceptedForIdempotentProcessing() {
        response(receipt("").replace("VALID", "VALIDATED"));
        assertNull(verify().reviewReason());
    }
    @Test void mismatchedTransactionIsRejected() {
        response(receipt("").replace("Wtest123", "Wother123"));
        assertThrows(IllegalArgumentException.class, this::verify);
    }
    @Test void mismatchedValidationIdIsRejected() {
        response(receipt("").replace("val123", "val999"));
        assertThrows(IllegalArgumentException.class, this::verify);
    }
    @Test void wrongStoreIsRejected() {
        response(receipt(",\"store_id\":\"other-store\""));
        assertThrows(IllegalArgumentException.class, this::verify);
    }
    @Test void unverifiedStatusIsRejected() {
        response(receipt("").replace("VALID", "INVALID_TRANSACTION"));
        assertThrows(IllegalArgumentException.class, this::verify);
    }
    @Test void mismatchedAmountRequiresReview() {
        response(receipt("").replace("101.00", "100.00"));
        assertEquals("AMOUNT_MISMATCH", verify().reviewReason());
    }
    @Test void wrongCurrencyRequiresReview() {
        response(receipt("").replace("BDT", "USD"));
        assertEquals("CURRENCY_MISMATCH", verify().reviewReason());
    }
    @Test void riskyPaymentRequiresReview() {
        response(receipt("").replace("\"risk_level\":\"0\"", "\"risk_level\":\"1\""));
        assertEquals("GATEWAY_RISK", verify().reviewReason());
    }
    @Test void outageRemainsRetryable() {
        server.expect(anything()).andRespond(withServerError());
        assertThrows(org.springframework.web.client.RestClientException.class, this::verify);
    }
    @Test void authenticFailureCanBeReconciledWithoutBrowserStatus() {
        server.expect(requestTo(containsString("merchantTransIDvalidationAPI.php?tran_id=Wtest123")))
                .andRespond(withSuccess("{\"APIConnect\":\"DONE\",\"element\":[{\"tran_id\":\"Wtest123\",\"status\":\"FAILED\"}]}", MediaType.APPLICATION_JSON));
        assertEquals("FAILED", gateway().notification("Wtest123", null, BigDecimal.TEN).terminalStatus());
    }
    @Test void pendingGatewayResultCannotReleaseSeats() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"APIConnect\":\"DONE\",\"element\":[{\"tran_id\":\"Wtest123\",\"status\":\"PENDING\"}]}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> gateway().notification("Wtest123", null, BigDecimal.TEN));
    }
    @Test void startSendsServerAmountAndAllCallbacks() {
        server.expect(requestTo("https://sandbox-gw.sslcommerz.com/gwprocess/v4/api.php"))
                .andExpect(content().string(containsString("total_amount=101.00")))
                .andExpect(content().string(containsString("ipn_url=")))
                .andExpect(content().string(containsString("cancel_url=")))
                .andRespond(withSuccess("{\"status\":\"SUCCESS\",\"GatewayPageURL\":\"https://sandbox.sslcommerz.com/pay/test\"}", MediaType.APPLICATION_JSON));
        assertEquals("https://sandbox.sslcommerz.com/pay/test", gateway().start(
                new BookingService.Start(1L, "Wtest123", new BigDecimal("101.00"), "Test Traveler", "test@example.com"),
                new SandboxPaymentGateway.Billing("01712345678", "Test Road", "Dhaka", "1200")));
        server.verify();
    }
    @Test void gatewayCannotRedirectToUntrustedSite() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"status\":\"SUCCESS\",\"GatewayPageURL\":\"https://attacker.example/pay\"}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> gateway().start(
                new BookingService.Start(1L, "Wtest123", new BigDecimal("101.00"), "Test", "test@example.com"),
                new SandboxPaymentGateway.Billing("01712345678", "Test", "Dhaka", "1200")));
    }
}
