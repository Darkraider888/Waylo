package com.mehedi.waylo.booking;

import com.mehedi.waylo.entity.Trip;
import com.mehedi.waylo.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter @Setter @NoArgsConstructor
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "traveler_id", nullable = false)
    private User traveler;

    @Column(name = "seat_count", nullable = false)
    private int seatCount;

    @Column(name = "price_per_seat", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerSeat;

    @Column(name = "trip_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal tripTotal;

    @Column(name = "advance_percent", nullable = false)
    private int advancePercent;

    @Column(name = "advance_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal advanceAmount;

    @Column(name = "platform_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "payable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal payableAmount;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "hold_expires_at", nullable = false)
    private LocalDateTime holdExpiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "balance_paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal balancePaidAmount = BigDecimal.ZERO;

    public BigDecimal remainingBalance() {
        BigDecimal paid = balancePaidAmount == null ? BigDecimal.ZERO : balancePaidAmount;
        return tripTotal.subtract(advanceAmount).subtract(paid).max(BigDecimal.ZERO);
    }

    public boolean isPaidInFull() {
        return "CONFIRMED".equals(status) && remainingBalance().signum() == 0;
    }

    public boolean isHoldExpired() {
        return "EXPIRED".equals(status) || ("PENDING_PAYMENT".equals(status)
                && !holdExpiresAt.isAfter(LocalDateTime.now(java.time.ZoneId.of("Asia/Dhaka"))));
    }
}
