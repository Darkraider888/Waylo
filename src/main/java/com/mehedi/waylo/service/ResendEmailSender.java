package com.mehedi.waylo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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

/** HTTPS transactional-email sender; Render Free blocks outbound SMTP. */
@Service
public class ResendEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);
    private final RestClient client;
    private final String apiKey;
    private final String from;

    public ResendEmailSender(
            @Value("${waylo.resend.api-key:}") String apiKey,
            @Value("${waylo.resend.from:onboarding@resend.dev}") String from) {

        this.apiKey = apiKey;
        this.from = from;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.client = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .requestFactory(requestFactory)
                .build();
    }

    public void sendPasswordReset(
            String recipientEmail,
            String fullName,
            String resetLink,
            int validityMinutes) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Password-reset email is not configured: WAYLO_RESEND_API_KEY is missing");
        }
        if (from == null || from.isBlank()) {
            throw new IllegalStateException(
                    "Password-reset email is not configured: WAYLO_RESEND_FROM is missing");
        }

        String body = "Hello " + fullName + ",\n\n"
                + "We received a request to reset your Waylo account password.\n\n"
                + "Click this link to create a new password:\n\n"
                + resetLink + "\n\n"
                + "This link expires in " + validityMinutes
                + " minutes and can only be used once.\n\n"
                + "If you did not request this change, ignore this email.\n\n"
                + "Waylo - Explore. Connect. Go.";

        try {
            client.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", from,
                            "to", List.of(recipientEmail),
                            "subject", "Waylo - Reset Your Password",
                            "text", body
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            // Do not log API keys, reset tokens, recipient addresses or HTTP response bodies.
            log.warn("Resend refused reset email. HTTP status: {}", ex.getStatusCode().value());
            throw new IllegalStateException("Password-reset email provider rejected the request");
        } catch (RestClientException ex) {
            log.warn("Resend connection failed: {}", ex.getClass().getSimpleName());
            throw new IllegalStateException("Password-reset email provider could not be reached");
        }
    }
}
