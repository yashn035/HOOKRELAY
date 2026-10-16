-- V2: Add payload transformation rules column to endpoints table
-- Allows per-endpoint JSONPath -> template mapping for outbound payload transformations

ALTER TABLE endpoints ADD COLUMN transform_rules TEXT;
