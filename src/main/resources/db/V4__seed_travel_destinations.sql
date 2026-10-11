-- Add choices for the organizer form without modifying existing destinations.
INSERT INTO destinations (name, description, location)
SELECT 'Sreemangal', 'Tea gardens and green landscapes.', 'Sylhet, Bangladesh'
WHERE NOT EXISTS (SELECT 1 FROM destinations WHERE name = 'Sreemangal');
INSERT INTO destinations (name, description, location)
SELECT 'Chattogram', 'City and coastal journeys.', 'Chattogram, Bangladesh'
WHERE NOT EXISTS (SELECT 1 FROM destinations WHERE name = 'Chattogram');
INSERT INTO destinations (name, description, location)
SELECT 'Cox''s Bazar', 'Beach journeys on the Bay of Bengal.', 'Cox''s Bazar, Bangladesh'
WHERE NOT EXISTS (SELECT 1 FROM destinations WHERE name = 'Cox''s Bazar');
