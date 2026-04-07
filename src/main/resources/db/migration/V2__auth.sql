CREATE TABLE email_verifications
(
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    code       VARCHAR(10)  NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    token      VARCHAR(500) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_verifications_email ON email_verifications (email);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
