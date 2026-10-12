package com.mehedi.waylo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Brevo HTTPS transactional email. Does not use SMTP; works on Render Free. */
@Service
public class BrevoEmailSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);

    private final RestClient client;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;

    public BrevoEmailSender(
            @Value("${WAYLO_BREVO_API_KEY:}") String apiKey,
            @Value("${WAYLO_BREVO_FROM_EMAIL:}") String fromEmail,
            @Value("${WAYLO_BREVO_FROM_NAME:Waylo}") String fromName) {

        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.client = RestClient.builder()
                .baseUrl("https://api.brevo.com")
                .requestFactory(requestFactory)
                .build();
    }

    public void sendPasswordReset(
            String recipientEmail,
            String fullName,
            String resetLink,
            int validityMinutes) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("WAYLO_BREVO_API_KEY is not configured");
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("WAYLO_BREVO_FROM_EMAIL is not configured");
        }

        String body = "Hello " + fullName + ",\n\n"
                + "We received a request to reset your Waylo account password.\n\n"
                + "Click this link to create a new password:\n\n"
                + resetLink + "\n\n"
                + "This link expires in " + validityMinutes
                + " minutes and can only be used once.\n\n"
                + "If you did not request this change, ignore this email.\n\n"
                + "Waylo - Explore. Connect. Go.";

        // Brevo expects api-key (not Authorization: Bearer), and textContent
        // (not Resend's 'text'). The sender must be added/verified in Brevo.
        try {
            client.post()
                    .uri("/v3/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "sender", Map.of(
                                    "email", fromEmail,
                                    "name", fromName == null ? "Waylo" : fromName),
                            "to", List.of(Map.of("email", recipientEmail)),
                            "subject", "Waylo - Reset Your Password",
                            "textContent", body
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            // Never log API keys, reset URLs/tokens, recipient addresses or response bodies.
            log.warn("Brevo refused a password-reset email; HTTP status: {}",
                    ex.getStatusCode().value());
            throw new IllegalStateException("Brevo rejected the password-reset email request");
        } catch (RestClientException ex) {
            log.warn("Brevo connection error type: {}", ex.getClass().getSimpleName());
            throw new IllegalStateException("Brevo could not be reached for password reset");
        }
    }
}
