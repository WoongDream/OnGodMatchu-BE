CREATE TABLE users
(
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    nickname       VARCHAR(100) NOT NULL,
    password       VARCHAR(255),
    provider       VARCHAR(20)  NOT NULL,
    provider_id    VARCHAR(255),
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE quizzes
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    category      VARCHAR(100) NOT NULL,
    thumbnail_url VARCHAR(500),
    play_count    INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE questions
(
    id            BIGSERIAL PRIMARY KEY,
    quiz_id       BIGINT       NOT NULL REFERENCES quizzes (id),
    order_num     INT          NOT NULL,
    image_url     VARCHAR(500),
    question_text TEXT,
    answer        VARCHAR(500) NOT NULL
);

CREATE INDEX idx_quizzes_user_id ON quizzes (user_id);
CREATE INDEX idx_quizzes_category ON quizzes (category);
CREATE INDEX idx_questions_quiz_id ON questions (quiz_id);
