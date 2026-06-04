package com.ongodmatchu.domain.admin.service;

import com.ongodmatchu.domain.user.entity.Role;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.entity.UserStatus;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;

/**
 * 사용자 관리(정지/권한 변경) 권한 경계를 한 곳에 모은다. UI 노출 제어와 무관하게 서버에서 항상 검증한다.
 *
 * <ul>
 *   <li>ADMIN 은 USER 만 정지/관리, 다른 ADMIN·OWNER 대상 액션 불가
 *   <li>OWNER 는 USER/ADMIN 관리 가능하나 OWNER(본인 포함) 대상 액션 불가
 *   <li>역할 임명/해임은 OWNER 전용. 단 ADMIN 의 자가 사임(ADMIN→USER)은 허용
 *   <li>탈퇴(WITHDRAWN) 사용자는 모든 관리 액션 불가
 * </ul>
 */
public final class AdminAuthorization {

  private AdminAuthorization() {}

  /** 정지/해제 가능 여부. 위반 시 예외. */
  public static void assertCanSuspend(User actor, User target) {
    assertManageableTarget(target);
    if (actor.getId().equals(target.getId())) {
      throw new BusinessException(ErrorCode.ADMIN_FORBIDDEN);
    }
    switch (actor.getRole()) {
      case OWNER -> {
        // USER/ADMIN 모두 정지 가능 (OWNER 대상은 assertManageableTarget 에서 차단)
      }
      case ADMIN -> {
        if (target.getRole() != Role.USER) {
          throw new BusinessException(ErrorCode.ADMIN_FORBIDDEN);
        }
      }
      default -> throw new BusinessException(ErrorCode.ADMIN_FORBIDDEN);
    }
  }

  /** 권한 변경(임명/해임/자가 사임) 가능 여부. 위반 시 예외. */
  public static void assertCanChangeRole(User actor, User target, Role newRole) {
    if (target.getStatus() == UserStatus.WITHDRAWN) {
      throw new BusinessException(ErrorCode.ADMIN_TARGET_INVALID);
    }
    if (newRole == Role.OWNER || target.getRole() == Role.OWNER) {
      // OWNER 임명·강등은 불가 (OWNER 는 시딩으로만 존재)
      throw new BusinessException(ErrorCode.ADMIN_FORBIDDEN);
    }
    boolean selfResign =
        actor.getId().equals(target.getId())
            && actor.getRole() == Role.ADMIN
            && newRole == Role.USER;
    if (selfResign) {
      return;
    }
    if (actor.getRole() != Role.OWNER) {
      // 자가 사임을 제외한 임명/해임은 OWNER 전용
      throw new BusinessException(ErrorCode.ADMIN_FORBIDDEN);
    }
  }

  private static void assertManageableTarget(User target) {
    if (target.getStatus() == UserStatus.WITHDRAWN) {
      throw new BusinessException(ErrorCode.ADMIN_TARGET_INVALID);
    }
    if (target.getRole() == Role.OWNER) {
      throw new BusinessException(ErrorCode.ADMIN_FORBIDDEN);
    }
  }
}
