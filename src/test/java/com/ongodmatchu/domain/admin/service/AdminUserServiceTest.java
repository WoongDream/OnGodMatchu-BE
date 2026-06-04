package com.ongodmatchu.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.admin.dto.AdminUserResponse;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.Role;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

  @InjectMocks private AdminUserService adminUserService;
  @Mock private UserRepository userRepository;

  private User user(long id, Role role) {
    return user(id, role, true);
  }

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

  private ErrorCode errorCodeOf(Throwable t) {
    return ((BusinessException) t).getErrorCode();
  }

  // ============ getUsers ============

  @Test
  @DisplayName("getUsers — searchForAdmin 결과를 AdminUserResponse 로 매핑하고 인자를 위임한다")
  void getUsers_mapsResultAndDelegatesArgs() {
    User u = user(2L, Role.USER);
    given(
            userRepository.searchForAdmin(
                eq("ACTIVE"), eq(Role.USER), eq("kim"), any(LocalDateTime.class), any()))
        .willReturn(new PageImpl<>(List.of(u)));

    Page<AdminUserResponse> result =
        adminUserService.getUsers("ACTIVE", Role.USER, "kim", PageRequest.of(0, 20));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).userId()).isEqualTo(u.getPublicId());
    assertThat(result.getContent().get(0).role()).isEqualTo("USER");
    assertThat(result.getContent().get(0).status()).isEqualTo("ACTIVE");
    then(userRepository)
        .should()
        .searchForAdmin(eq("ACTIVE"), eq(Role.USER), eq("kim"), any(LocalDateTime.class), any());
  }

  @Test
  @DisplayName("getUsers — 빈 query 는 null 로 정규화되어 위임된다")
  void getUsers_blankQuery_normalizedToNull() {
    given(
            userRepository.searchForAdmin(
                isNull(), isNull(), isNull(), any(LocalDateTime.class), any()))
        .willReturn(new PageImpl<>(List.of()));

    Page<AdminUserResponse> result =
        adminUserService.getUsers(null, null, "   ", PageRequest.of(0, 20));

    assertThat(result.getContent()).isEmpty();
    then(userRepository)
        .should()
        .searchForAdmin(isNull(), isNull(), isNull(), any(LocalDateTime.class), any());
  }

  @Test
  @DisplayName("getUsers — pageSize 50 초과 요청은 50 으로 cap, query trim 후 위임")
  void getUsers_pageSizeCappedAndQueryTrimmed() {
    given(
            userRepository.searchForAdmin(
                isNull(), isNull(), eq("hong"), any(LocalDateTime.class), any()))
        .willReturn(new PageImpl<>(List.of()));

    adminUserService.getUsers(null, null, "  hong  ", PageRequest.of(0, 200));

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(userRepository)
        .should()
        .searchForAdmin(isNull(), isNull(), eq("hong"), any(LocalDateTime.class), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(50);
  }

  // ============ suspend ============

  @Test
  @DisplayName("suspend — OWNER 가 USER 정지 시 days 만큼 정지되고 status=SUSPENDED 반환")
  void suspend_success() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    AdminUserResponse result = adminUserService.suspend(1L, target.getPublicId(), 7);

    assertThat(target.isSuspended()).isTrue();
    assertThat(target.getSuspendedUntil()).isAfter(LocalDateTime.now().plusDays(6));
    assertThat(result.status()).isEqualTo("SUSPENDED");
  }

  @Test
  @DisplayName("suspend — actor 미존재 시 USER_NOT_FOUND")
  void suspend_actorNotFound() {
    given(userRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> adminUserService.suspend(1L, UUID.randomUUID(), 7))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
    then(userRepository).should(never()).findByPublicId(any());
  }

  @Test
  @DisplayName("suspend — target 미존재 시 USER_NOT_FOUND")
  void suspend_targetNotFound() {
    User actor = user(1L, Role.OWNER);
    UUID targetId = UUID.randomUUID();
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(targetId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> adminUserService.suspend(1L, targetId, 7))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("suspend — ADMIN actor 가 ADMIN target 정지 시 권한 위반 ADMIN_FORBIDDEN 전파")
  void suspend_adminActorAdminTarget_forbidden() {
    User actor = user(1L, Role.ADMIN);
    User target = user(2L, Role.ADMIN);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    assertThatThrownBy(() -> adminUserService.suspend(1L, target.getPublicId(), 7))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    assertThat(target.isSuspended()).isFalse();
  }

  @Test
  @DisplayName("suspend — 탈퇴 target 정지 시 ADMIN_TARGET_INVALID 전파")
  void suspend_withdrawnTarget_targetInvalid() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER, false);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    assertThatThrownBy(() -> adminUserService.suspend(1L, target.getPublicId(), 7))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.ADMIN_TARGET_INVALID);
  }

  // ============ unsuspend ============

  @Test
  @DisplayName("unsuspend — 정지 해제 후 isSuspended=false, status=ACTIVE")
  void unsuspend_success() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    target.suspendUntil(LocalDateTime.now().plusDays(3));
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    AdminUserResponse result = adminUserService.unsuspend(1L, target.getPublicId());

    assertThat(target.isSuspended()).isFalse();
    assertThat(result.status()).isEqualTo("ACTIVE");
  }

  @Test
  @DisplayName("unsuspend — 권한 위반(USER actor) 시 ADMIN_FORBIDDEN 전파")
  void unsuspend_userActor_forbidden() {
    User actor = user(1L, Role.USER);
    User target = user(2L, Role.USER);
    target.suspendUntil(LocalDateTime.now().plusDays(3));
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    assertThatThrownBy(() -> adminUserService.unsuspend(1L, target.getPublicId()))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    assertThat(target.isSuspended()).isTrue();
  }

  // ============ changeRole ============

  @Test
  @DisplayName("changeRole — OWNER 가 USER 를 ADMIN 으로 임명")
  void changeRole_ownerAppointsAdmin() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    AdminUserResponse result = adminUserService.changeRole(1L, target.getPublicId(), Role.ADMIN);

    assertThat(target.getRole()).isEqualTo(Role.ADMIN);
    assertThat(result.role()).isEqualTo("ADMIN");
  }

  @Test
  @DisplayName("changeRole — ADMIN 자가 사임(ADMIN→USER)")
  void changeRole_adminSelfResign() {
    User actor = user(1L, Role.ADMIN);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(actor.getPublicId())).willReturn(Optional.of(actor));

    AdminUserResponse result = adminUserService.changeRole(1L, actor.getPublicId(), Role.USER);

    assertThat(actor.getRole()).isEqualTo(Role.USER);
    assertThat(result.role()).isEqualTo("USER");
  }

  @Test
  @DisplayName("changeRole — ADMIN 이 다른 USER 를 ADMIN 으로 임명 시 ADMIN_FORBIDDEN 전파")
  void changeRole_adminAppointsAdmin_forbidden() {
    User actor = user(1L, Role.ADMIN);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    assertThatThrownBy(() -> adminUserService.changeRole(1L, target.getPublicId(), Role.ADMIN))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    assertThat(target.getRole()).isEqualTo(Role.USER);
  }

  @Test
  @DisplayName("changeRole — newRole=OWNER 임명 시 ADMIN_FORBIDDEN 전파")
  void changeRole_toOwner_forbidden() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    assertThatThrownBy(() -> adminUserService.changeRole(1L, target.getPublicId(), Role.OWNER))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.ADMIN_FORBIDDEN);
    assertThat(target.getRole()).isEqualTo(Role.USER);
  }

  @Test
  @DisplayName("changeRole — actor 미존재 시 USER_NOT_FOUND")
  void changeRole_actorNotFound() {
    given(userRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> adminUserService.changeRole(1L, UUID.randomUUID(), Role.ADMIN))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
    then(userRepository).should(never()).findByPublicId(any());
  }
}
