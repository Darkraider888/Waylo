ALTER TABLE destinations
    ADD COLUMN district VARCHAR(120) NULL,
    ADD COLUMN country VARCHAR(120) NULL,
    ADD COLUMN latitude DECIMAL(10, 7) NULL,
    ADD COLUMN longitude DECIMAL(10, 7) NULL;

UPDATE destinations
SET
    district = TRIM(
            SUBSTRING_INDEX(
                    COALESCE(location, ''),
                    ',',
                    1
            )
               ),
    country = 'Bangladesh'
WHERE country IS NULL
  AND location LIKE '%, Bangladesh';

CREATE INDEX idx_destinations_name
    ON destinations(name);