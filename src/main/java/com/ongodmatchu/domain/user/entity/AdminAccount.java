package com.ongodmatchu.domain.user.entity;

import java.util.UUID;

/** 시스템 관리자 계정의 고정 식별자 — V17 시드와 동일. 탈퇴 사용자의 퀴즈 작성자 이전 대상이다. */
public final class AdminAccount {

  public static final UUID PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

  /** 시드된 시스템 관리자 닉네임 — NicknamePolicy 의 reserved 차단 대상. */
  public static final String NICKNAME = "관리자";

  private AdminAccount() {}
}
