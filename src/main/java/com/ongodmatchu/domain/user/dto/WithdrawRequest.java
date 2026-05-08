package com.ongodmatchu.domain.user.dto;

import com.ongodmatchu.domain.user.entity.WithdrawalReason;

/**
 * 회원탈퇴 요청.
 *
 * <ul>
 *   <li>{@code currentPassword} — LOCAL 계정 필수, OAuth 는 생략 가능
 *   <li>{@code confirmationPhrase} — 모달 타이핑 검증용, 정확히 "탈퇴하겠습니다." 와 일치해야 함
 *   <li>{@code deleteOwnQuizzes} — true 면 본인 퀴즈+관련(질문/시도/스타/댓글) 일괄 삭제, false(default) 면 작성자를 시스템
 *       관리자 계정으로 이전
 *   <li>{@code reason} — 탈퇴 이유(enum), 익명 통계로 저장. null 허용
 *   <li>{@code reasonText} — ETC 시 자유 입력. null/길이 500 까지
 * </ul>
 */
public record WithdrawRequest(
    String currentPassword,
    String confirmationPhrase,
    Boolean deleteOwnQuizzes,
    WithdrawalReason reason,
    String reasonText) {

  public boolean shouldDeleteOwnQuizzes() {
    return Boolean.TRUE.equals(deleteOwnQuizzes);
  }
}
