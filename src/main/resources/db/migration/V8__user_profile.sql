ALTER TABLE users
    ADD COLUMN profile_image_key VARCHAR(500),
    ADD COLUMN bio               VARCHAR(100),
    ADD COLUMN is_profile_public BOOLEAN   NOT NULL DEFAULT TRUE,
    ADD COLUMN last_active_at    TIMESTAMP;
