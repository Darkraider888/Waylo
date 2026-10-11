package com.mehedi.waylo.booking;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_attempts")
@Getter @Setter @NoArgsConstructor
public class PaymentAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 30)
    private String transactionId;

    @Column(name = "expected_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "gateway_validation_id", unique = true, length = 80)
    private String gatewayValidationId;

    @Column(name = "bank_transaction_id", length = 80)
    private String bankTransactionId;

    @Column(name = "payment_kind", nullable = false, length = 20)
    private String paymentKind = "ADVANCE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "review_reason", length = 80)
    private String reviewReason;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
}
