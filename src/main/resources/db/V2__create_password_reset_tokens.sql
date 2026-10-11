CREATE TABLE password_reset_tokens (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       user_id BIGINT NOT NULL,
                                       token_hash CHAR(64) NOT NULL UNIQUE,
                                       expires_at DATETIME(6) NOT NULL,
                                       used BOOLEAN NOT NULL DEFAULT FALSE,

                                       CONSTRAINT fk_reset_token_user
                                           FOREIGN KEY (user_id)
                                               REFERENCES users(id)
                                               ON DELETE CASCADE
);

CREATE INDEX idx_reset_token_user
    ON password_reset_tokens(user_id);