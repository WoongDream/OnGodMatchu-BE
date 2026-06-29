package com.ongodmatchu.domain.user.dto;

import com.ongodmatchu.domain.user.entity.User;
import java.util.UUID;

/** 응답 작성자 표시 헬퍼 — 탈퇴 사용자는 nickname/profileImageUrl/publicId 를 익명화 텍스트/null 로 치환한다. */
public final class UserDisplay {

  public static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

  /** {@code user==null} 또는 {@code !user.isActive()} 면 "탈퇴한 사용자", 외엔 원래 닉네임. */
  public static String nicknameOf(User user) {
    if (user == null || !user.isActive()) {
      return WITHDRAWN_NICKNAME;
    }
    return user.getNickname();
  }

  /**
   * 탈퇴(비활성)/null 사용자면 publicId 를 노출하지 않는다(null). FE 가 프로필 모달/공개 프로필 진입을 비활성화하는 신호로 사용 — 탈퇴 사용자는
   * 프로필이 없어 진입 시 404 이므로 애초에 클릭을 막는다.
   */
  public static UUID publicIdOf(User user) {
    if (user == null || !user.isActive()) {
      return null;
    }
    return user.getPublicId();
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
