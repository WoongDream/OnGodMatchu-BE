package com.ongodmatchu.domain.admin.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 백오피스 사용자 관리 이력의 변경 유형. 한 번의 저장에 여러 변경이 묶이면 MULTIPLE. 알림만 보낸 경우는 changeType 자체가 null. */
@Getter
@RequiredArgsConstructor
public enum AdminUserChangeType {
  ROLE_CHANGE("역할 변경"),
  ACCOUNT_STATUS_CHANGE("계정 상태 변경"),
  PROFILE_IMAGE_RESET("프로필 이미지 초기화"),
  NICKNAME_RESET("닉네임 초기화"),
  BIO_RESET("자기소개 초기화"),
  MULTIPLE("회원 정보 변경");

  private final String label;
}
