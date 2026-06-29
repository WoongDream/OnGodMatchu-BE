ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD COLUMN suspended_until TIMESTAMP NULL;

-- 기존 시스템 관리자 계정(V17 시드, publicId 고정)을 OWNER 로 승격.
-- 비밀번호는 시크릿이라 마이그레이션에 두지 않고 부트스트랩(OwnerAccountInitializer)에서 환경변수로 주입한다.
UPDATE users
SET role = 'OWNER'
WHERE public_id = '00000000-0000-0000-0000-000000000000';
