package com.ongodmatchu.domain.user.dto;

/**
 * 회원탈퇴 요청. LOCAL/OAuth 무관하게 본인 이메일 인증 코드로 재확인.
 *
 * <ul>
 *   <li>{@code verificationCode} — 사전에 {@code POST /api/users/me/withdrawal-code} 로 발급받은 6자리 코드
 *       (필수)
 *   <li>{@code confirmationPhrase} — 모달 타이핑 검증, 정확히 "탈퇴하겠습니다." 와 일치 (필수)
 *   <li>{@code deleteOwnQuizzes} — true 면 본인 퀴즈+관련(질문/시도/스타/댓글) 일괄 삭제, false(default) 면 작성자를 시스템
 *       관리자 계정으로 이전
 *   <li>{@code reasonText} — 탈퇴 이유 자유 텍스트(NFC + 500자). 익명 통계로 저장. null 허용
 * </ul>
 */
public record WithdrawRequest(
    String verificationCode,
    String confirmationPhrase,
    Boolean deleteOwnQuizzes,
    String reasonText) {

  public boolean shouldDeleteOwnQuizzes() {
    return Boolean.TRUE.equals(deleteOwnQuizzes);
  }
}
