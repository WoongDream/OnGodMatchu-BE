package com.ongodmatchu.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 프로필 모달용 타인 프로필 요약. 닉네임/소개 + 풀이 기록(풀어봄·정답률) + 만든 퀴즈(개수·총플레이·받은스타). 통계는 전부 PUBLIC 퀴즈 기준 (비공개 퀴즈
 * 제외). 비공개 프로필도 통계는 노출하며, FE 가 {@code isProfilePublic} 으로 "프로필 보러가기" 버튼 노출만 분기한다.
 */
public record PublicProfileSummaryResponse(
    UUID userId,
    String nickname,
    @Schema(description = "프로필 이미지 presigned URL (key 없으면 서버측 default)") String profileImageUrl,
    @Schema(description = "한 줄 소개. 없으면 null", nullable = true) String bio,
    @Schema(description = "프로필 공개 여부. false 면 FE 가 '프로필 보러가기' 버튼 숨김") boolean isProfilePublic,
    @Schema(description = "풀어본 횟수 — PUBLIC 퀴즈 attempt 수") long solvedCount,
    @Schema(description = "평균 정답률 (0~100). PUBLIC 풀이 0회면 null", nullable = true)
        Double avgSolveRate,
    @Schema(description = "만든 PUBLIC 퀴즈 개수") long quizCount,
    @Schema(description = "만든 PUBLIC 퀴즈 총 플레이 수") long totalPlayCount,
    @Schema(description = "만든 PUBLIC 퀴즈 받은 스타 합계") long totalStarCount,
    @Schema(description = "사용자 역할 — USER/ADMIN/OWNER. FE 가 OWNER/ADMIN 만 닉네임 위 배지로 표시")
        String role) {}
