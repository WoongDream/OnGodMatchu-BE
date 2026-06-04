-- 백오피스 사용자 관리 이력 (수정 내용 + 관련 알림 페어)
CREATE TABLE admin_user_histories (
    id                      BIGSERIAL PRIMARY KEY,
    actor_id                BIGINT      NOT NULL REFERENCES users (id),
    target_user_id          BIGINT      NOT NULL REFERENCES users (id),
    change_type             VARCHAR(40) NULL,
    detail                  TEXT        NULL,
    related_notification_id BIGINT      NULL REFERENCES user_notifications (id),
    created_at              TIMESTAMP   NOT NULL
);

-- 대상 유저별 최신순 이력 조회용
CREATE INDEX idx_admin_user_histories_target
    ON admin_user_histories (target_user_id, created_at DESC);
