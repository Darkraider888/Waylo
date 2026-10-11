-- SSLCommerz SANDBOX booking holds and auditable verification records.
-- Never edit V1-V8 after Flyway has applied them.
CREATE TABLE bookings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    traveler_id BIGINT NOT NULL,
    seat_count INT NOT NULL,
    price_per_seat DECIMAL(10,2) NOT NULL,
    trip_total DECIMAL(12,2) NOT NULL,
    advance_percent INT NOT NULL,
    advance_amount DECIMAL(12,2) NOT NULL,
    platform_fee DECIMAL(12,2) NOT NULL,
    payable_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(24) NOT NULL,
    hold_expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    CONSTRAINT fk_booking_trip FOREIGN KEY (trip_id) REFERENCES trips(id),
    CONSTRAINT fk_booking_traveler FOREIGN KEY (traveler_id) REFERENCES users(id),
    CONSTRAINT chk_booking_seats CHECK (seat_count BETWEEN 1 AND 6),
    CONSTRAINT chk_booking_advance CHECK (advance_percent BETWEEN 10 AND 100),
    CONSTRAINT chk_booking_amounts CHECK (trip_total >= 0 AND advance_amount >= 0 AND platform_fee >= 0 AND payable_amount >= 0),
    INDEX idx_booking_trip_status (trip_id, status, hold_expires_at),
    INDEX idx_booking_traveler (traveler_id, created_at)
);

CREATE TABLE payment_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    transaction_id VARCHAR(30) NOT NULL,
    expected_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(24) NOT NULL,
    gateway_validation_id VARCHAR(80) NULL,
    bank_transaction_id VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL,
    verified_at DATETIME(6) NULL,
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT uq_payment_transaction UNIQUE (transaction_id),
    CONSTRAINT uq_payment_validation UNIQUE (gateway_validation_id),
    INDEX idx_payment_booking (booking_id)
);
