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

  /**
   * 권한 변경(임명/해임/자가 사임) 가능 여부. 위반 시 예외.
   *
   * <ul>
   *   <li>OWNER 는 USER/ADMIN/OWNER 어느 역할로든 임명·해제 가능 (OWNER 부여·강등 포함)
   *   <li>ADMIN 의 자가 사임(ADMIN→USER)만 예외적으로 허용, 그 외 ADMIN 의 역할 변경은 불가
   *   <li>OWNER 본인의 역할 변경은 불가 (자기 강등으로 인한 권한 잠금 방지)
   *   <li>시스템 계정·탈퇴 사용자는 대상 불가
   * </ul>
   */
  public static void assertCanChangeRole(User actor, User target, Role newRole) {
    if (target.isSystem()) {
      throw new BusinessException(ErrorCode.ADMIN_TARGET_INVALID);
    }
    if (target.getStatus() == UserStatus.WITHDRAWN) {
      throw new BusinessException(ErrorCode.ADMIN_TARGET_INVALID);
    }
    boolean selfResign =
        actor.getId().equals(target.getId())
            && actor.getRole() == Role.ADMIN
            && newRole == Role.USER;
    if (selfResign) {
      return;
    }
    // 자가 사임을 제외한 임명/해제(OWNER·ADMIN)는 OWNER 전용
    if (actor.getRole() != Role.OWNER) {
      throw new BusinessException(ErrorCode.ADMIN_FORBIDDEN);
    }
    // OWNER 본인의 역할 변경은 불가
    if (actor.getId().equals(target.getId())) {
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
