CREATE TABLE upload_meta
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    s3_key        VARCHAR(500) NOT NULL UNIQUE,
    original_name VARCHAR(255),
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    completed_at  TIMESTAMP
);

CREATE INDEX idx_upload_meta_user_id ON upload_meta (user_id);
CREATE INDEX idx_upload_meta_status_created_at ON upload_meta (status, created_at);
