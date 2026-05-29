-- 공용 이미지 편집 모달: 원본 보존 + 크롭/변환 메타 저장
-- 기존 *_key 컬럼 = 보여주는(크롭) 이미지로 유지, 슬롯마다 원본 key + transform(jsonb) 추가.
-- 모두 nullable (additive). 기존 행은 original/transform = NULL → "원본 미보존" 으로 graceful degrade.
-- transform 은 FE 소유 opaque JSON ({v, flipH, rotate, crop{x,y,width,height}}). BE 는 의미 해석하지 않음.

ALTER TABLE quizzes
    ADD COLUMN original_thumbnail_key VARCHAR(500),
    ADD COLUMN thumbnail_transform    JSONB;

ALTER TABLE questions
    ADD COLUMN original_image_key        VARCHAR(500),
    ADD COLUMN image_transform           JSONB,
    ADD COLUMN original_answer_image_key VARCHAR(500),
    ADD COLUMN answer_image_transform    JSONB;

ALTER TABLE users
    ADD COLUMN original_profile_image_key VARCHAR(500),
    ADD COLUMN profile_image_transform    JSONB;
