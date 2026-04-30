UPDATE users u
SET nickname = u.nickname || '_' || u.id
WHERE EXISTS (SELECT 1
              FROM users u2
              WHERE u2.nickname = u.nickname
                AND u2.id <> u.id);

ALTER TABLE users
    ADD CONSTRAINT uk_users_nickname UNIQUE (nickname);
