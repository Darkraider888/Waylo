ALTER TABLE posts
    ADD COLUMN image_public_id VARCHAR(255) NULL;

ALTER TABLE users
    ADD COLUMN avatar_public_id VARCHAR(255) NULL;

ALTER TABLE trips
    ADD COLUMN cover_image_public_id VARCHAR(255) NULL;