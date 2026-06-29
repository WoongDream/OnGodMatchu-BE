-- 사용자 알림 (관리자 발송 → 사용자 modal 수신)
CREATE TABLE user_notifications (
    id             BIGSERIAL PRIMARY KEY,
    target_user_id BIGINT       NOT NULL REFERENCES users (id),
    sender_id      BIGINT       REFERENCES users (id),
    type           VARCHAR(20)  NOT NULL,
    title          VARCHAR(200) NOT NULL,
    content        TEXT         NOT NULL,
    read_at        TIMESTAMP    NULL,
    created_at     TIMESTAMP    NOT NULL
);

-- 수신자별 미확인 알림 조회용 (target_user_id, read_at, created_at)
CREATE INDEX idx_user_notifications_pending
    ON user_notifications (target_user_id, read_at, created_at);
