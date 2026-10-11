-- A pending one-trip organizer can suggest a destination without creating a public
-- destination. Only platform admin approval adds/reuses an actual MySQL destination.
-- Apply after V10. Do not edit previously applied migrations.
ALTER TABLE trip_proposals
    MODIFY COLUMN destination_id BIGINT NULL,
    ADD COLUMN suggested_destination_name VARCHAR(150) NULL,
    ADD COLUMN suggested_district VARCHAR(120) NULL,
    ADD COLUMN suggested_country VARCHAR(120) NULL,
    ADD COLUMN suggested_description VARCHAR(1000) NULL,
    ADD COLUMN suggested_latitude DECIMAL(10,7) NULL,
    ADD COLUMN suggested_longitude DECIMAL(10,7) NULL;
