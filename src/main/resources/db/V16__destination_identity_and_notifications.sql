-- Existing destinations, including duplicate records, remain intact.
-- One canonical key is reserved for each existing normalized identity.
CREATE TABLE destination_keys (
 name_key VARCHAR(150) NOT NULL, district_key VARCHAR(255) NOT NULL, country_key VARCHAR(120) NOT NULL,
 destination_id BIGINT NOT NULL, PRIMARY KEY(name_key,district_key,country_key),
 FOREIGN KEY(destination_id) REFERENCES destinations(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
INSERT INTO destination_keys(name_key,district_key,country_key,destination_id)
SELECT LOWER(TRIM(REGEXP_REPLACE(name,'[[:space:]]+',' '))),
 LOWER(TRIM(REGEXP_REPLACE(COALESCE(district,location,''),'[[:space:]]+',' '))),
 LOWER(TRIM(REGEXP_REPLACE(COALESCE(country,'Bangladesh'),'[[:space:]]+',' '))),MIN(id)
FROM destinations GROUP BY 1,2,3;
ALTER TABLE destinations ADD COLUMN image_public_id VARCHAR(255) NULL;
CREATE TABLE user_notifications (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, message VARCHAR(500) NOT NULL,
 link VARCHAR(200) NOT NULL, read_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(user_id) REFERENCES users(id), INDEX idx_notification_user(user_id,created_at)
);
