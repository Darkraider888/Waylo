package com.mehedi.waylo.booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** SSLCommerz TEST environment only. There is deliberately no production endpoint switch. */
@Service
public class SandboxPaymentGateway {
    private static final String INIT_URL = "https://sandbox-gw.sslcommerz.com/gwprocess/v4/api.php";
    private static final String VERIFY_URL = "https://sandbox.sslcommerz.com/validator/api/validationserverAPI.php";
    private final RestClient http;

    @org.springframework.beans.factory.annotation.Autowired
    public SandboxPaymentGateway(
            @Value("${waylo.sslcommerz.enabled:false}") boolean enabled,
            @Value("${waylo.sslcommerz.store-id:}") String storeId,
            @Value("${waylo.sslcommerz.store-password:}") String password,
            @Value("${waylo.public-url:http://localhost:8080}") String publicUrl) {
        this(buildClient(), enabled, storeId, password, publicUrl);
    }

    SandboxPaymentGateway(RestClient http, boolean enabled, String storeId, String password, String publicUrl) {
        this.http = http;
        this.enabled = enabled;
        this.storeId = storeId;
        this.storePassword = password;
        this.publicUrl = publicUrl;
    }

    private static RestClient buildClient() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(factory).build();
    }

    private final boolean enabled;
    private final String storeId;
    private final String storePassword;
    private final String publicUrl;

    public boolean ready() {
        if (!enabled || storeId.isBlank() || storePassword.isBlank()) return false;
        try {
            URI uri = URI.create(publicUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && !uri.getHost().equalsIgnoreCase("localhost")
                    && uri.getHost().contains(".")
                    && !uri.getHost().matches("[0-9.]+")
                    && !uri.getHost().endsWith(".local")
                    && !uri.getHost().endsWith(".localhost")
                    && uri.getPort() == -1 && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && (uri.getPath().isEmpty() || uri.getPath().equals("/"))
                    && publicUrl.length() < 200;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private void requireReady() {
        if (!ready()) {
            throw new IllegalStateException("Sandbox checkout is not configured with a public HTTPS callback URL.");
        }
    }

    private String callback(String path) {
        return publicUrl.replaceAll("/+$", "") + path;
    }

    private String required(String value, int length, String name) {
        if (value == null || value.isBlank() || value.trim().length() > length)
            throw new IllegalArgumentException("Enter a valid " + name + ".");
        return value.trim();
    }

    public record Billing(String phone, String address, String city, String postalCode) {}
    public record Verification(String transactionId, String validationId,
                               BigDecimal amount, String bankTransactionId, String reviewReason) {
        public Verification(String transactionId, String validationId, BigDecimal amount, String bankTransactionId) {
            this(transactionId, validationId, amount, bankTransactionId, null);
        }
    }

    public String start(BookingService.Start start, Billing billing) {
        requireReady();
        String phone = required(billing.phone(), 20, "phone number");
        String address = required(billing.address(), 50, "address");
        String city = required(billing.city(), 50, "city");
        String postcode = required(billing.postalCode(), 15, "postcode");
        if (!phone.matches("[+]?[0-9]{10,15}") || !postcode.matches("[A-Za-z0-9 -]{3,15}"))
            throw new IllegalArgumentException("Check the phone number and postcode.");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("store_id", storeId);
        body.add("store_passwd", storePassword);
        body.add("total_amount", start.payableAmount().toPlainString());
        body.add("currency", "BDT");
        body.add("tran_id", start.transactionId());
        body.add("success_url", callback("/payments/sslcommerz/success"));
        body.add("fail_url", callback("/payments/sslcommerz/fail"));
        body.add("cancel_url", callback("/payments/sslcommerz/cancel"));
        body.add("ipn_url", callback("/payments/sslcommerz/ipn"));
        body.add("cus_name", required(start.travelerName(), 50, "name"));
        body.add("cus_email", required(start.travelerEmail(), 50, "email"));
        body.add("cus_add1", address);
        body.add("cus_city", city);
        body.add("cus_postcode", postcode);
        body.add("cus_country", "Bangladesh");
        body.add("cus_phone", phone);
        body.add("shipping_method", "NO");
        body.add("product_name", "Waylo trip reservation");
        body.add("product_category", "Tourism");
        body.add("product_profile", "general");

        // Credentials only travel over HTTPS server-to-server. Never expose them in JS or HTML.
        Map<?, ?> response = http.post().uri(INIT_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body).retrieve().body(Map.class);

        if (response == null || !"SUCCESS".equals(Objects.toString(response.get("status"), "")))
            throw new IllegalStateException("SSLCommerz sandbox could not initiate checkout.");
        String url = Objects.toString(response.get("GatewayPageURL"), "");
        URI redirect;
        try { redirect = URI.create(url); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("Invalid gateway redirect URL."); }
        if (!"https".equalsIgnoreCase(redirect.getScheme())
                || !("sandbox.sslcommerz.com".equalsIgnoreCase(redirect.getHost())
                || "sandbox-gw.sslcommerz.com".equalsIgnoreCase(redirect.getHost()))
                || redirect.getRawUserInfo() != null || redirect.getPort() != -1)
            throw new IllegalStateException("Unexpected gateway redirect destination.");
        return url;
    }

    public record Notification(Verification payment, String terminalStatus) { }

    public Notification notification(String reference, String validationId, BigDecimal expected) {
        requireReady();
        if (reference == null || !reference.matches("[A-Za-z0-9]{5,30}"))
            throw new IllegalArgumentException("Invalid payment reference.");
        if (validationId != null && !validationId.isBlank())
            return new Notification(verify(reference, validationId, expected), null);
        URI uri = UriComponentsBuilder.fromUriString(
                "https://sandbox.sslcommerz.com/validator/api/merchantTransIDvalidationAPI.php")
                .queryParam("tran_id", reference).queryParam("store_id", storeId)
                .queryParam("store_passwd", storePassword).queryParam("format", "json")
                .build().encode().toUri();
        Map<?, ?> response = http.get().uri(uri).retrieve().body(Map.class);
        if (response == null || !"DONE".equals(response.get("APIConnect"))
                || !(response.get("element") instanceof java.util.List<?> rows) || rows.isEmpty())
            throw new IllegalStateException("Gateway transaction status is unavailable.");
        String terminal = "CANCELLED";
        boolean pending = false;
        for (Object value : rows) {
            if (!(value instanceof Map<?, ?> row) || !reference.equals(row.get("tran_id")))
                throw new IllegalArgumentException("Gateway transaction reference mismatch.");
            String status = Objects.toString(row.get("status"), "");
            if ("VALID".equals(status) || "VALIDATED".equals(status)) {
                return new Notification(verify(reference, Objects.toString(row.get("val_id"), ""), expected), null);
            }
            if ("FAILED".equals(status)) terminal = "FAILED";
            else if (!"CANCELLED".equals(status)) pending = true;
        }
        if (pending) throw new IllegalStateException("Gateway transaction is still pending.");
        return new Notification(null, terminal);
    }

    /** Never trust browser or IPN status/amount; call SSLCommerz validation API. */
    public Verification verify(String transactionId, String validationId, BigDecimal expectedAmount) {
        requireReady();
        if (transactionId == null || !transactionId.matches("[A-Za-z0-9]{5,30}")
                || validationId == null || !validationId.matches("[A-Za-z0-9]{5,80}"))
            throw new IllegalArgumentException("Invalid gateway transaction reference.");

        String uri = UriComponentsBuilder.fromUriString(VERIFY_URL)
                .queryParam("val_id", validationId)
                .queryParam("store_id", storeId)
                .queryParam("store_passwd", storePassword)
                .queryParam("format", "json")
                .build().encode().toUriString();

        // Do not log this URL; it contains the sandbox store password.
        Map<?, ?> response = http.get().uri(URI.create(uri)).retrieve().body(Map.class);
        if (response == null) throw new IllegalArgumentException("Gateway verification was unavailable.");
        String status = Objects.toString(response.get("status"), "");
        if (!"VALID".equals(status) && !"VALIDATED".equals(status))
            throw new IllegalArgumentException("Gateway has not verified this payment.");
        if (!transactionId.equals(Objects.toString(response.get("tran_id"), ""))
                || !validationId.equals(Objects.toString(response.get("val_id"), "")))
            throw new IllegalArgumentException("Gateway verification data did not match.");
        if (response.get("store_id") != null &&
                !storeId.equals(Objects.toString(response.get("store_id"))))
            throw new IllegalArgumentException("Wrong gateway store.");

        BigDecimal amount;
        try { amount = new BigDecimal(Objects.toString(response.get("amount"), "")); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Gateway amount missing."); }
        String review = null;
        if (expectedAmount.compareTo(amount) != 0) review = "AMOUNT_MISMATCH";
        else if (!"BDT".equals(Objects.toString(response.get("currency"), ""))) review = "CURRENCY_MISMATCH";
        else if (response.get("currency_type") != null
                && !"BDT".equals(Objects.toString(response.get("currency_type")))) review = "CURRENCY_MISMATCH";
        else if (!"0".equals(Objects.toString(response.get("risk_level"), ""))) review = "GATEWAY_RISK";
        String bankId = Objects.toString(response.get("bank_tran_id"), "");
        if (bankId.length() > 80) bankId = bankId.substring(0, 80);
        return new Verification(transactionId, validationId, amount, bankId, review);
    }
}
