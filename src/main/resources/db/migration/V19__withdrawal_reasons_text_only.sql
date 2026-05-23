-- 탈퇴 이유를 주관식 텍스트로만 저장하도록 단순화. ENUM 코드 컬럼 제거.
ALTER TABLE withdrawal_reasons
    DROP COLUMN reason_code;
