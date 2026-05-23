-- 회원탈퇴 이메일 인증 코드. 가입용 email_verifications 와 분리해 본인 인증 컨텍스트만 다룬다.
CREATE TABLE withdrawal_verifications
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    code        VARCHAR(6)  NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    consumed_at TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_withdrawal_verifications_user_created
    ON withdrawal_verifications (user_id, created_at DESC);
