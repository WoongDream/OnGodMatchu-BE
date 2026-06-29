-- 문의하기 (사용자 문의 접수 → BO 처리 → 답변은 user_notifications 재사용·연결)
CREATE TABLE inquiries (
    id         BIGSERIAL     PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES users (id),
    title      VARCHAR(50)   NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    status     VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP     NOT NULL,
    updated_at TIMESTAMP     NULL
);

-- 본인 문의 목록 (user_id, 최신순)
CREATE INDEX idx_inquiries_user_created ON inquiries (user_id, created_at DESC);
-- BO 목록/필터 (status, 최신순)
CREATE INDEX idx_inquiries_status_created ON inquiries (status, created_at DESC);

-- 답변 = 기존 알림을 문의에 연결 (1문의 : N알림). 문의 무관 알림은 NULL.
ALTER TABLE user_notifications
    ADD COLUMN related_inquiry_id BIGINT NULL REFERENCES inquiries (id);

CREATE INDEX idx_user_notifications_inquiry
    ON user_notifications (related_inquiry_id);
