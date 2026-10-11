-- Trip-scoped organizer approval, membership-only group chat and sandbox balance collection.
-- Applied AFTER V9. Do not modify already-applied migrations.
CREATE TABLE trip_proposals (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    applicant_id BIGINT NOT NULL,
    destination_id BIGINT NOT NULL,
    approved_trip_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    departure_date DATETIME(6) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    capacity INT NOT NULL,
    motivation VARCHAR(1500) NOT NULL,
    document_type VARCHAR(20) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500) NULL,
    reviewed_by BIGINT NULL,
    applied_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6) NULL,
    CONSTRAINT fk_proposal_applicant FOREIGN KEY (applicant_id) REFERENCES users(id),
    CONSTRAINT fk_proposal_destination FOREIGN KEY (destination_id) REFERENCES destinations(id),
    CONSTRAINT fk_proposal_trip FOREIGN KEY (approved_trip_id) REFERENCES trips(id),
    CONSTRAINT fk_proposal_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id),
    CONSTRAINT uq_proposal_approved_trip UNIQUE (approved_trip_id),
    CONSTRAINT chk_proposal_capacity CHECK (capacity BETWEEN 1 AND 1000),
    CONSTRAINT chk_proposal_price CHECK (price > 0),
    INDEX idx_proposal_applicant (applicant_id, applied_at),
    INDEX idx_proposal_status (status, applied_at)
);

CREATE TABLE trip_group_members (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    booking_id BIGINT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at DATETIME(6) NOT NULL,
    removed_at DATETIME(6) NULL,
    removed_by BIGINT NULL,
    removal_reason VARCHAR(500) NULL,
    CONSTRAINT fk_group_member_trip FOREIGN KEY (trip_id) REFERENCES trips(id),
    CONSTRAINT fk_group_member_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_group_member_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT fk_group_member_removed_by FOREIGN KEY (removed_by) REFERENCES users(id),
    CONSTRAINT uq_group_member UNIQUE (trip_id, user_id),
    INDEX idx_group_member_access (trip_id, active)
);

CREATE TABLE trip_group_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content VARCHAR(1500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    removed_at DATETIME(6) NULL,
    removed_by BIGINT NULL,
    CONSTRAINT fk_group_message_trip FOREIGN KEY (trip_id) REFERENCES trips(id),
    CONSTRAINT fk_group_message_sender FOREIGN KEY (sender_id) REFERENCES users(id),
    CONSTRAINT fk_group_message_removed_by FOREIGN KEY (removed_by) REFERENCES users(id),
    INDEX idx_group_message_cursor (trip_id, id)
);

ALTER TABLE bookings
    ADD COLUMN balance_paid_amount DECIMAL(12,2) NOT NULL DEFAULT 0;

ALTER TABLE payment_attempts
    ADD COLUMN payment_kind VARCHAR(20) NOT NULL DEFAULT 'ADVANCE';

-- Confirmed reservations created under V9 are eligible for the same group access.
INSERT INTO trip_group_members (trip_id, user_id, booking_id, active, joined_at)
SELECT b.trip_id, b.traveler_id, MIN(b.id), TRUE, CURRENT_TIMESTAMP(6)
FROM bookings b
JOIN payment_attempts a ON a.booking_id = b.id
    AND a.status = 'VERIFIED' AND a.payment_kind = 'ADVANCE'
WHERE b.status = 'CONFIRMED'
GROUP BY b.trip_id, b.traveler_id;
