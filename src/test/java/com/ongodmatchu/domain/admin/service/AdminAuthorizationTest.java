package com.ongodmatchu.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.Role;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdminAuthorizationTest {

  /** id/role 을 지정해 활성(ACTIVE) 사용자 생성. */
  private User user(long id, Role role) {
    return user(id, role, true);
  }

  /** isActive=false 면 WITHDRAWN 파생 상태가 된다. */
  private User user(long id, Role role, boolean active) {
    User u =
        User.builder()
            .email("u" + id + "@example.com")
            .nickname("유저" + id)
            .password("hashed")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(u, "id", id);
    ReflectionTestUtils.setField(u, "publicId", UUID.randomUUID());
    ReflectionTestUtils.setField(u, "role", role);
    ReflectionTestUtils.setField(u, "isActive", active);
    return u;
  }

  @Nested
  @DisplayName("assertCanSuspend")
  class AssertCanSuspend {

    @Test
    @DisplayName("탈퇴(WITHDRAWN) 대상 — ADMIN_TARGET_INVALID")
    void target_withdrawn_targetInvalid() {
      User actor = user(1L, Role.OWNER);
      User target = user(2L, Role.USER, false);

      assertThatThrownBy(() -> AdminAuthorization.assertCanSuspend(actor, target))
          .isInstanceOf(BusinessException.class)
          .extracting(this::outer)
          .isEqualTo(ErrorCode.ADMIN_TARGET_INVALID);
    }

    private ErrorCode outer(Throwable t) {
      return ((BusinessException) t).getErrorCode();
    }

    @Test
    @DisplayName("OWNER 대상 — ADMIN_FORBIDDEN")
    void target_owner_forbidden() {
      User actor = user(1L, Role.OWNER);
      User target = user(2L, Role.OWNER);

      assertThatThrownBy(() -> AdminAuthorization.assertCanSuspend(actor, target))
          .isInstanceOf(BusinessException.class)
          .extracting(this::outer)
          .isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }

    @Test
    @DisplayName("검증 순서 — OWNER 이면서 WITHDRAWN 이면 WITHDRAWN(ADMIN_TARGET_INVALID) 가 먼저")
    void target_ownerAndWithdrawn_withdrawnFirst() {
      User actor = user(1L, Role.OWNER);
      User target = user(2L, Role.OWNER, false);

      assertThatThrownBy(() -> AdminAuthorization.assertCanSuspend(actor, target))
          .isInstanceOf(BusinessException.class)
          .extracting(this::outer)
          .isEqualTo(ErrorCode.ADMIN_TARGET_INVALID);
    }

    @Test
    @DisplayName("자기 자신 대상 — ADMIN_FORBIDDEN")
    void self_forbidden() {
      User actor = user(1L, Role.OWNER);

      assertThatThrownBy(() -> AdminAuthorization.assertCanSuspend(actor, actor))
          .isInstanceOf(BusinessException.class)
          .extracting(this::outer)
          .isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }

    @Test
    @DisplayName("OWNER 가 USER 정지 — 통과")
    void owner_suspends_user_ok() {
      User actor = user(1L, Role.OWNER);
      User target = user(2L, Role.USER);

      assertThatCode(() -> AdminAuthorization.assertCanSuspend(actor, target))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OWNER 가 ADMIN 정지 — 통과")
    void owner_suspends_admin_ok() {
      User actor = user(1L, Role.OWNER);
      User target = user(2L, Role.ADMIN);

      assertThatCode(() -> AdminAuthorization.assertCanSuspend(actor, target))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ADMIN 이 USER 정지 — 통과")
    void admin_suspends_user_ok() {
      User actor = user(1L, Role.ADMIN);
      User target = user(2L, Role.USER);

      assertThatCode(() -> AdminAuthorization.assertCanSuspend(actor, target))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ADMIN 이 다른 ADMIN 정지 — ADMIN_FORBIDDEN")
    void admin_suspends_admin_forbidden() {
      User actor = user(1L, Role.ADMIN);
      User target = user(2L, Role.ADMIN);

      assertThatThrownBy(() -> AdminAuthorization.assertCanSuspend(actor, target))
          .isInstanceOf(BusinessException.class)
          .extracting(this::outer)
          .isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }

    @Test
    @DisplayName("USER actor — ADMIN_FORBIDDEN")
    void user_actor_forbidden() {
      User actor = user(1L, Role.USER);
      User target = user(2L, Role.USER);

      assertThatThrownBy(() -> AdminAuthorization.assertCanSuspend(actor, target))
          .isInstanceOf(BusinessException.class)
          .extracting(this::outer)
          .isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }
  }

  @Nested
  @DisplayName("assertCanChangeRole")
  class AssertCanChangeRole {

    private ErrorCode outer(Throwable t) {
      return ((BusinessException) t).getErrorCode();
    }

    @Test
    @DisplayName("탈퇴(WITHDRAWN) 대상 — ADMIN_TARGET_INVALID")
    void target_withdrawn_targetInvalid() {
      User actor = user(1L, Role.OWNER);
      User target = user(2L, Role.USER, false);

      assertThatThrownBy(() -> AdminAuthorization.assertCanChangeRole(actor, target, Role.ADMIN))
          .isInstanceOf(BusinessException.class)
          .extracting(this::outer)
          .isEqualTo(ErrorCode.ADMIN_TARGET_INVALID);
    }

    @Test
    @DisplayName("newRole == OWNER — ADMIN_FORBIDDEN")
    void newRole_owner_forbidden() {
      User actor = user(1L, Role.OWNER);
      User target = user(2L, Role.USER);

      assertThatThrownBy(() -> AdminAuthorization.assertCanChangeRole(actor, target, Role.OWNER))
          .isInstanceOf(BusinessException.class)
          .extracting(this::outer)
          .isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }

    @Test
    @DisplayName("target.role == OWNER — ADMIN_FORBIDDEN")
    void target_owner_forbidden() {
      User actor = user(1L, Role.OWNER);
      User target = user(2L, Role.OWNER);

      assertThatThrownBy(() -> AdminAuthorization.assertCanChangeRole(actor, target, Role.USER))
          .isInstanceOf(BusinessException.class)
          .extracting(this::outer)
          .isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }

    @Test
    @DisplayName("ADMIN 자가 사임(ADMIN→USER) — 통과")
    void admin_selfResign_ok() {
      User actor = user(1L, Role.ADMIN);

      assertThatCode(() -> AdminAuthorization.assertCanChangeRole(actor, actor, Role.USER))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OWNER 가 USER→ADMIN 임명 — 통과")
    void owner_appointsAdmin_ok() {
      User actor = user(1L, Role.OWNER);
      User target = user(2L, Role.USER);

      assertThatCode(() -> AdminAuthorization.assertCanChangeRole(actor, target, Role.ADMIN))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OWNER 가 ADMIN→USER 해임 — 통과")
    void owner_dismissesAdmin_ok() {
      User actor = user(1L, Role.OWNER);
      User target = user(2L, Role.ADMIN);

      assertThatCode(() -> AdminAuthorization.assertCanChangeRole(actor, target, Role.USER))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ADMIN 이 다른 USER 를 ADMIN 으로 임명 — ADMIN_FORBIDDEN")
    void admin_appointsAdmin_forbidden() {
      User actor = user(1L, Role.ADMIN);
      User target = user(2L, Role.USER);

      assertThatThrownBy(() -> AdminAuthorization.assertCanChangeRole(actor, target, Role.ADMIN))
          .isInstanceOf(BusinessException.class)
          .extracting(this::outer)
          .isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }

    @Test
    @DisplayName("ADMIN 이 다른 ADMIN 을 USER 로 해임 — ADMIN_FORBIDDEN")
    void admin_dismissesOtherAdmin_forbidden() {
      User actor = user(1L, Role.ADMIN);
      User target = user(2L, Role.ADMIN);

      assertThatThrownBy(() -> AdminAuthorization.assertCanChangeRole(actor, target, Role.USER))
          .isInstanceOf(BusinessException.class)
          .extracting(this::outer)
          .isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }

    @Test
    @DisplayName("USER actor — ADMIN_FORBIDDEN")
    void user_actor_forbidden() {
      User actor = user(1L, Role.USER);
      User target = user(2L, Role.USER);

      assertThatThrownBy(() -> AdminAuthorization.assertCanChangeRole(actor, target, Role.ADMIN))
          .isInstanceOf(BusinessException.class)
          .extracting(this::outer)
          .isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    }
  }
}
