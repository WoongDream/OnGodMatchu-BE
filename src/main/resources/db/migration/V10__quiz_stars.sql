CREATE TABLE quiz_stars
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL REFERENCES users (id),
    quiz_id    BIGINT    NOT NULL REFERENCES quizzes (id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_quiz_stars_user_quiz UNIQUE (user_id, quiz_id)
);

CREATE INDEX idx_quiz_stars_quiz ON quiz_stars (quiz_id);

ALTER TABLE quizzes
    ADD COLUMN star_count INT NOT NULL DEFAULT 0;
