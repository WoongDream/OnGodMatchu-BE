package com.ongodmatchu.domain.user.dto;

/**
 * 회원탈퇴 인증 코드 dry-run 검증 요청. 탈퇴 확정 전 인증 버튼 클릭 시 코드 유효성만 확인한다.
 *
 * <ul>
 *   <li>{@code verificationCode} — {@code POST /api/users/me/withdrawal-code} 로 발급받은 6자리 코드. 검증만 하고
 *       소비하지 않으므로 최종 {@code DELETE /me} 에서 동일 코드로 다시 검증·소비된다.
 * </ul>
 */
public record WithdrawalCodeVerifyRequest(String verificationCode) {}
