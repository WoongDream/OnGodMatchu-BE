CREATE TABLE visit_log
(
    id         BIGSERIAL PRIMARY KEY,
    anon_id    VARCHAR(36) NOT NULL,
    user_id    BIGINT      NULL REFERENCES users (id) ON DELETE SET NULL,
    path       VARCHAR(255) NOT NULL,
    visited_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_visit_log_anon_visited ON visit_log (anon_id, visited_at DESC);
CREATE INDEX idx_visit_log_visited ON visit_log (visited_at);
