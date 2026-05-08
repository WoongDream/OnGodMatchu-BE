CREATE TABLE quiz_comments
(
    id         BIGSERIAL PRIMARY KEY,
    quiz_id    BIGINT    NOT NULL REFERENCES quizzes (id),
    user_id    BIGINT    NOT NULL REFERENCES users (id),
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP NULL
);

CREATE INDEX idx_quiz_comments_quiz_created ON quiz_comments (quiz_id, created_at DESC);

ALTER TABLE quizzes
    ADD COLUMN comment_count INT NOT NULL DEFAULT 0;
