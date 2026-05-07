CREATE TABLE quiz_attempts
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT    NOT NULL REFERENCES users (id),
    quiz_id         BIGINT    NOT NULL REFERENCES quizzes (id),
    score           INT       NOT NULL,
    total_questions INT       NOT NULL,
    completed_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_quiz_attempts_user_completed ON quiz_attempts (user_id, completed_at DESC);
CREATE INDEX idx_quiz_attempts_quiz_completed ON quiz_attempts (quiz_id, completed_at DESC);
