ALTER TABLE users
    ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT FALSE;

-- 시스템 관리자 1행 시드 — publicId 고정. 탈퇴 사용자의 퀴즈 작성자 일괄 이전 대상.
-- password=NULL → LOCAL 비밀번호 로그인 불가, AuthService 와 CustomUserDetailsService 에서 isSystem=TRUE 추가 차단.
INSERT INTO users (
    public_id, email, nickname, provider,
    email_verified, is_profile_public, is_active, is_system
)
VALUES (
    '00000000-0000-0000-0000-000000000000', 'admin@system.local', '관리자', 'LOCAL',
    TRUE, TRUE, TRUE, TRUE
);
