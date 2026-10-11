ALTER TABLE users
    ADD COLUMN bio VARCHAR(500) NULL,
    ADD COLUMN profile_location VARCHAR(150) NULL,
    ADD COLUMN avatar_url VARCHAR(500) NULL;

CREATE TABLE posts (
                       id BIGINT NOT NULL AUTO_INCREMENT,
                       user_id BIGINT NOT NULL,
                       caption VARCHAR(2000) NOT NULL,
                       image_url VARCHAR(500) NULL,
                       destination_id BIGINT NULL,
                       trip_id BIGINT NULL,
                       created_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),

                       PRIMARY KEY (id),

                       CONSTRAINT fk_posts_user
                           FOREIGN KEY (user_id)
                               REFERENCES users(id),

                       CONSTRAINT fk_posts_destination
                           FOREIGN KEY (destination_id)
                               REFERENCES destinations(id),

                       CONSTRAINT fk_posts_trip
                           FOREIGN KEY (trip_id)
                               REFERENCES trips(id),

                       INDEX idx_posts_created (created_at, id),
                       INDEX idx_posts_user_created (user_id, created_at),
                       INDEX idx_posts_destination (destination_id),
                       INDEX idx_posts_trip (trip_id)
);