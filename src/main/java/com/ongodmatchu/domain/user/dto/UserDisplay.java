package com.ongodmatchu.domain.user.dto;

import com.ongodmatchu.domain.user.entity.User;

/** 응답 작성자 표시 헬퍼 — 탈퇴 사용자는 nickname/profileImageUrl 을 익명화 텍스트/null 로 치환한다. */
public final class UserDisplay {

  public static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

  /** {@code user==null} 또는 {@code !user.isActive()} 면 "탈퇴한 사용자", 외엔 원래 닉네임. */
  public static String nicknameOf(User user) {
    if (user == null || !user.isActive()) {
      return WITHDRAWN_NICKNAME;
    }
    return user.getNickname();
  }

  /** 호출자가 미리 해석한 presigned URL 을 받아, 탈퇴 사용자면 null 로 마스킹한다. 호출자가 null 시 default fallback 으로 처리. */
  public static String profileImageUrlOf(User user, String resolvedUrl) {
    if (user == null || !user.isActive()) {
      return null;
    }
    return resolvedUrl;
  }

  private UserDisplay() {}
}
