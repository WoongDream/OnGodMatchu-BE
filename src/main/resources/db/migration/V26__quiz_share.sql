CREATE TABLE quiz_share
(
    id        BIGSERIAL PRIMARY KEY,
    quiz_id   BIGINT      NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,
    user_id   BIGINT      NULL REFERENCES users (id) ON DELETE CASCADE,
    anon_id   VARCHAR(36) NULL,
    shared_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_quiz_share_user_or_anon CHECK (user_id IS NOT NULL OR anon_id IS NOT NULL)
);

CREATE UNIQUE INDEX uq_quiz_share_user ON quiz_share (quiz_id, user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_quiz_share_anon ON quiz_share (quiz_id, anon_id) WHERE anon_id IS NOT NULL;
CREATE INDEX idx_quiz_share_quiz ON quiz_share (quiz_id);
