CREATE TABLE users (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       full_name VARCHAR(100) NOT NULL,
                       email VARCHAR(150) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
                       enabled BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE destinations (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              name VARCHAR(150) NOT NULL,
                              description TEXT,
                              image_url VARCHAR(500),
                              location VARCHAR(255)
);

CREATE TABLE trips (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       title VARCHAR(200) NOT NULL,
                       description TEXT,
                       destination_id BIGINT NOT NULL,
                       organizer_id BIGINT NOT NULL,
                       departure_date DATETIME NOT NULL,
                       price DECIMAL(10,2) NOT NULL,
                       capacity INT NOT NULL,
                       status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

                       CONSTRAINT fk_trip_destination
                           FOREIGN KEY (destination_id)
                               REFERENCES destinations(id),

                       CONSTRAINT fk_trip_organizer
                           FOREIGN KEY (organizer_id)
                               REFERENCES users(id),

                       CONSTRAINT chk_trip_capacity
                           CHECK (capacity > 0),

                       CONSTRAINT chk_trip_price
                           CHECK (price >= 0)
);