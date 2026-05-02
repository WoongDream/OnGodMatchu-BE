CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE users
    ADD COLUMN public_id UUID;

UPDATE users
SET public_id = gen_random_uuid()
WHERE public_id IS NULL;

ALTER TABLE users
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN public_id SET DEFAULT gen_random_uuid(),
    ADD CONSTRAINT users_public_id_unique UNIQUE (public_id);

CREATE INDEX idx_users_public_id ON users (public_id);

ALTER TABLE quizzes
    ADD COLUMN public_id UUID;

UPDATE quizzes
SET public_id = gen_random_uuid()
WHERE public_id IS NULL;

ALTER TABLE quizzes
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN public_id SET DEFAULT gen_random_uuid(),
    ADD CONSTRAINT quizzes_public_id_unique UNIQUE (public_id);

CREATE INDEX idx_quizzes_public_id ON quizzes (public_id);
