package com.ongodmatchu.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.admin.dto.AdminUserDetailResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserSummaryResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserUpdateRequest;
import com.ongodmatchu.domain.admin.dto.AdminUserUpdateRequest.SuspensionUpdate;
import com.ongodmatchu.domain.admin.dto.MonthlyUserStatResponse;
import com.ongodmatchu.domain.admin.dto.SendNotificationRequest;
import com.ongodmatchu.domain.admin.entity.AdminUserChangeType;
import com.ongodmatchu.domain.admin.entity.AdminUserHistory;
import com.ongodmatchu.domain.admin.repository.AdminUserHistoryRepository;
import com.ongodmatchu.domain.notification.entity.NotificationType;
import com.ongodmatchu.domain.notification.entity.UserNotification;
import com.ongodmatchu.domain.notification.repository.UserNotificationRepository;
import com.ongodmatchu.domain.quiz.repository.QuizAttemptRepository;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.quiz.repository.QuizRepository.QuizAggregateRow;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.Role;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.domain.user.service.ProfileImageInitializer;
import com.ongodmatchu.domain.user.service.RandomNicknameGenerator;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.s3.S3Service;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
  @Mock private QuizRepository quizRepository;
  @Mock private QuizAttemptRepository quizAttemptRepository;
  @Mock private UserNotificationRepository userNotificationRepository;
  @Mock private AdminUserHistoryRepository adminUserHistoryRepository;
  @Mock private ProfileImageInitializer profileImageInitializer;
  @Mock private RandomNicknameGenerator randomNicknameGenerator;
  @Mock private S3Service s3Service;

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
  @DisplayName("changeRole — OWNER 가 USER 를 OWNER 로 임명 (정책 변경: OWNER 부여 허용)")
  void changeRole_toOwner_ok() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    AdminUserResponse result = adminUserService.changeRole(1L, target.getPublicId(), Role.OWNER);

    assertThat(target.getRole()).isEqualTo(Role.OWNER);
    assertThat(result.role()).isEqualTo("OWNER");
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

  // ============ getUserDetail ============

  private QuizAggregateRow agg(long quizCount, long plays, long stars) {
    return new QuizAggregateRow() {
      @Override
      public long getQuizCount() {
        return quizCount;
      }

      @Override
      public long getPlays() {
        return plays;
      }

      @Override
      public long getStars() {
        return stars;
      }

      @Override
      public long getComments() {
        return 0L;
      }

      @Override
      public long getShares() {
        return 0L;
      }
    };
  }

  @Test
  @DisplayName("getUserDetail — 프로필 + 플레이 기록 + 만든 퀴즈 통계를 모아 반환")
  void getUserDetail_aggregatesProfileAndStats() {
    User target = user(2L, Role.USER);
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));
    given(quizRepository.aggregateByUserId(2L)).willReturn(agg(3L, 120L, 45L));
    given(quizAttemptRepository.countByUserId(2L)).willReturn(7L);
    given(quizAttemptRepository.avgSolveRateOf(2L)).willReturn(82.5);

    AdminUserDetailResponse result = adminUserService.getUserDetail(target.getPublicId());

    assertThat(result.userId()).isEqualTo(target.getPublicId());
    assertThat(result.solvedCount()).isEqualTo(7L);
    assertThat(result.avgSolveRate()).isEqualTo(82.5);
    assertThat(result.quizCount()).isEqualTo(3L);
    assertThat(result.totalPlayCount()).isEqualTo(120L);
    assertThat(result.totalStarCount()).isEqualTo(45L);
  }

  @Test
  @DisplayName("getUserDetail — 탈퇴 유저는 status=WITHDRAWN, withdrawnAt 채워짐")
  void getUserDetail_withdrawnUser_fillsWithdrawnAt() {
    User target = user(2L, Role.USER);
    target.withdraw("anon@deleted", "탈퇴회원");
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));
    given(quizRepository.aggregateByUserId(2L)).willReturn(agg(0L, 0L, 0L));
    given(quizAttemptRepository.countByUserId(2L)).willReturn(0L);
    given(quizAttemptRepository.avgSolveRateOf(2L)).willReturn(null);

    AdminUserDetailResponse result = adminUserService.getUserDetail(target.getPublicId());

    assertThat(result.status()).isEqualTo("WITHDRAWN");
    assertThat(result.withdrawnAt()).isNotNull();
  }

  @Test
  @DisplayName("getUserDetail — 시스템 계정은 USER_NOT_FOUND 로 마스킹")
  void getUserDetail_systemAccount_masked() {
    User target = user(2L, Role.USER);
    ReflectionTestUtils.setField(target, "isSystem", true);
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    assertThatThrownBy(() -> adminUserService.getUserDetail(target.getPublicId()))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
    then(quizRepository).should(never()).aggregateByUserId(any());
  }

  // ============ updateUser ============

  private AdminUserUpdateRequest update(
      Role role,
      SuspensionUpdate suspension,
      boolean resetProfileImage,
      boolean resetNickname,
      boolean resetBio,
      SendNotificationRequest notification) {
    return new AdminUserUpdateRequest(
        role, suspension, resetProfileImage, resetNickname, resetBio, notification);
  }

  private SendNotificationRequest notification() {
    return new SendNotificationRequest(NotificationType.INFO, "제목", "내용");
  }

  @Test
  @DisplayName("updateUser — 역할만 변경 시 changeType=ROLE_CHANGE 이력 1행 기록")
  void updateUser_roleOnly_recordsRoleChange() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    adminUserService.updateUser(
        1L, target.getPublicId(), update(Role.ADMIN, null, false, false, false, null));

    assertThat(target.getRole()).isEqualTo(Role.ADMIN);
    ArgumentCaptor<AdminUserHistory> captor = ArgumentCaptor.forClass(AdminUserHistory.class);
    then(adminUserHistoryRepository).should().save(captor.capture());
    assertThat(captor.getValue().getChangeType()).isEqualTo(AdminUserChangeType.ROLE_CHANGE);
    assertThat(captor.getValue().getRelatedNotification()).isNull();
  }

  @Test
  @DisplayName("updateUser — 닉네임 초기화 시 RandomNicknameGenerator 결과로 변경 + NICKNAME_RESET 이력")
  void updateUser_resetNickname_recordsNicknameReset() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));
    given(randomNicknameGenerator.generate()).willReturn("새닉네임");

    adminUserService.updateUser(
        1L, target.getPublicId(), update(null, null, false, true, false, null));

    assertThat(target.getNickname()).isEqualTo("새닉네임");
    ArgumentCaptor<AdminUserHistory> captor = ArgumentCaptor.forClass(AdminUserHistory.class);
    then(adminUserHistoryRepository).should().save(captor.capture());
    assertThat(captor.getValue().getChangeType()).isEqualTo(AdminUserChangeType.NICKNAME_RESET);
  }

  @Test
  @DisplayName("updateUser — 프로필 이미지 초기화 시 S3 객체 삭제 + clear + initialize 호출")
  void updateUser_resetProfileImage_deletesAndReinitializes() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    ReflectionTestUtils.setField(target, "profileImageKey", "key-1");
    ReflectionTestUtils.setField(target, "originalProfileImageKey", "key-2");
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    adminUserService.updateUser(
        1L, target.getPublicId(), update(null, null, true, false, false, null));

    then(s3Service).should().deleteQuietly("key-1");
    then(s3Service).should().deleteQuietly("key-2");
    then(profileImageInitializer).should().initialize(target);
    ArgumentCaptor<AdminUserHistory> captor = ArgumentCaptor.forClass(AdminUserHistory.class);
    then(adminUserHistoryRepository).should().save(captor.capture());
    assertThat(captor.getValue().getChangeType())
        .isEqualTo(AdminUserChangeType.PROFILE_IMAGE_RESET);
  }

  @Test
  @DisplayName("updateUser — 자기소개 초기화 시 bio=null + BIO_RESET 이력")
  void updateUser_resetBio_recordsBioReset() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    target.updateBio("기존 소개");
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    adminUserService.updateUser(
        1L, target.getPublicId(), update(null, null, false, false, true, null));

    assertThat(target.getBio()).isNull();
    ArgumentCaptor<AdminUserHistory> captor = ArgumentCaptor.forClass(AdminUserHistory.class);
    then(adminUserHistoryRepository).should().save(captor.capture());
    assertThat(captor.getValue().getChangeType()).isEqualTo(AdminUserChangeType.BIO_RESET);
  }

  @Test
  @DisplayName("updateUser — 미래 종료일로 정지 시 suspendedUntil 세팅 + ACCOUNT_STATUS_CHANGE 이력")
  void updateUser_suspendFuture_recordsStatusChange() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));
    OffsetDateTime until = OffsetDateTime.now(ZoneOffset.of("+09:00")).plusDays(3);

    adminUserService.updateUser(
        1L,
        target.getPublicId(),
        update(null, new SuspensionUpdate(true, until), false, false, false, null));

    assertThat(target.isSuspended()).isTrue();
    ArgumentCaptor<AdminUserHistory> captor = ArgumentCaptor.forClass(AdminUserHistory.class);
    then(adminUserHistoryRepository).should().save(captor.capture());
    assertThat(captor.getValue().getChangeType())
        .isEqualTo(AdminUserChangeType.ACCOUNT_STATUS_CHANGE);
  }

  @Test
  @DisplayName("updateUser — 정지 종료일이 과거면 INVALID_INPUT, 이력 미기록")
  void updateUser_suspendPast_invalidInput() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));
    OffsetDateTime past = OffsetDateTime.now(ZoneOffset.of("+09:00")).minusDays(1);

    assertThatThrownBy(
            () ->
                adminUserService.updateUser(
                    1L,
                    target.getPublicId(),
                    update(null, new SuspensionUpdate(true, past), false, false, false, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.INVALID_INPUT);
    then(adminUserHistoryRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("updateUser — 정지에 종료일 누락 시 INVALID_INPUT")
  void updateUser_suspendNullUntil_invalidInput() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    assertThatThrownBy(
            () ->
                adminUserService.updateUser(
                    1L,
                    target.getPublicId(),
                    update(null, new SuspensionUpdate(true, null), false, false, false, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.INVALID_INPUT);
  }

  @Test
  @DisplayName("updateUser — 변경 2개 이상이면 changeType=MULTIPLE 단일 이력")
  void updateUser_multipleChanges_recordsMultiple() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));
    given(randomNicknameGenerator.generate()).willReturn("새닉네임");

    adminUserService.updateUser(
        1L, target.getPublicId(), update(Role.ADMIN, null, false, true, true, null));

    ArgumentCaptor<AdminUserHistory> captor = ArgumentCaptor.forClass(AdminUserHistory.class);
    then(adminUserHistoryRepository).should().save(captor.capture());
    assertThat(captor.getValue().getChangeType()).isEqualTo(AdminUserChangeType.MULTIPLE);
    assertThat(captor.getValue().getDetail()).contains("\n");
  }

  @Test
  @DisplayName("updateUser — 변경 0 + 알림 함께 보내면 changeType=null, relatedNotification 채워진 이력")
  void updateUser_notificationOnly_nullChangeTypeWithNotification() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));
    UserNotification saved = mock(UserNotification.class);
    given(userNotificationRepository.save(any())).willReturn(saved);

    adminUserService.updateUser(
        1L, target.getPublicId(), update(null, null, false, false, false, notification()));

    ArgumentCaptor<AdminUserHistory> captor = ArgumentCaptor.forClass(AdminUserHistory.class);
    then(adminUserHistoryRepository).should().save(captor.capture());
    assertThat(captor.getValue().getChangeType()).isNull();
    assertThat(captor.getValue().getRelatedNotification()).isEqualTo(saved);
  }

  @Test
  @DisplayName("updateUser — 변경 0 + 알림 0 이면 이력 미기록")
  void updateUser_noChangeNoNotification_skipsHistory() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    adminUserService.updateUser(
        1L, target.getPublicId(), update(null, null, false, false, false, null));

    then(adminUserHistoryRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("updateUser — 동일 역할 재지정은 변경으로 치지 않아 권한검사/이력 없음")
  void updateUser_sameRole_noChange() {
    User actor = user(1L, Role.ADMIN);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    adminUserService.updateUser(
        1L, target.getPublicId(), update(Role.USER, null, false, false, false, null));

    assertThat(target.getRole()).isEqualTo(Role.USER);
    then(adminUserHistoryRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("updateUser — 시스템 계정 대상은 USER_NOT_FOUND 마스킹")
  void updateUser_systemTarget_masked() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    ReflectionTestUtils.setField(target, "isSystem", true);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    assertThatThrownBy(
            () ->
                adminUserService.updateUser(
                    1L, target.getPublicId(), update(Role.ADMIN, null, false, false, false, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  // ============ sendNotification ============

  @Test
  @DisplayName("sendNotification — 알림 저장 + changeType=null 이력(알림만)")
  void sendNotification_savesNotificationAndNullChangeTypeHistory() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));
    UserNotification saved = mock(UserNotification.class);
    given(userNotificationRepository.save(any())).willReturn(saved);

    adminUserService.sendNotification(1L, target.getPublicId(), notification());

    then(userNotificationRepository).should().save(any(UserNotification.class));
    ArgumentCaptor<AdminUserHistory> captor = ArgumentCaptor.forClass(AdminUserHistory.class);
    then(adminUserHistoryRepository).should().save(captor.capture());
    assertThat(captor.getValue().getChangeType()).isNull();
    assertThat(captor.getValue().getRelatedNotification()).isEqualTo(saved);
  }

  @Test
  @DisplayName("sendNotification — 탈퇴 대상은 ADMIN_TARGET_INVALID, 이력 미기록")
  void sendNotification_withdrawnTarget_targetInvalid() {
    User actor = user(1L, Role.OWNER);
    User target = user(2L, Role.USER, false);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(userRepository.findByPublicId(target.getPublicId())).willReturn(Optional.of(target));

    assertThatThrownBy(
            () -> adminUserService.sendNotification(1L, target.getPublicId(), notification()))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.ADMIN_TARGET_INVALID);
    then(adminUserHistoryRepository).should(never()).save(any());
  }

  // ============ getSummary ============

  @Test
  @DisplayName("getSummary — 활성/역할별/정지/이번달 신규를 repository 카운트로 집계")
  void getSummary_aggregatesCounts() {
    given(userRepository.countActive()).willReturn(100L);
    given(userRepository.countActiveByRole(Role.OWNER)).willReturn(1L);
    given(userRepository.countActiveByRole(Role.ADMIN)).willReturn(4L);
    given(userRepository.countActiveByRole(Role.USER)).willReturn(95L);
    given(userRepository.countSuspended(any(LocalDateTime.class))).willReturn(3L);
    given(userRepository.countJoinedBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
        .willReturn(12L);

    AdminUserSummaryResponse result = adminUserService.getSummary();

    assertThat(result.totalUsers()).isEqualTo(100L);
    assertThat(result.ownerCount()).isEqualTo(1L);
    assertThat(result.adminCount()).isEqualTo(4L);
    assertThat(result.userCount()).isEqualTo(95L);
    assertThat(result.suspendedCount()).isEqualTo(3L);
    assertThat(result.newThisMonth()).isEqualTo(12L);
  }

  // ============ getMonthlyStats ============

  @Test
  @DisplayName("getMonthlyStats — months 만큼 오래된 달→최신 달 순, 마지막이 이번 달")
  void getMonthlyStats_oldestToNewest() {
    lenient().when(userRepository.countActiveAsOf(any())).thenReturn(0L);
    lenient().when(userRepository.countJoinedBetween(any(), any())).thenReturn(0L);
    lenient().when(userRepository.countWithdrawnBetween(any(), any())).thenReturn(0L);

    List<MonthlyUserStatResponse> result = adminUserService.getMonthlyStats(3);

    assertThat(result).hasSize(3);
    String thisMonth = java.time.YearMonth.now(java.time.ZoneOffset.of("+09:00")).toString();
    assertThat(result.get(2).yearMonth()).isEqualTo(thisMonth);
    assertThat(result.get(0).yearMonth()).isLessThan(result.get(2).yearMonth());
  }

  @Test
  @DisplayName("getMonthlyStats — months<1 은 1로, >24 는 24로 cap")
  void getMonthlyStats_capsRange() {
    lenient().when(userRepository.countActiveAsOf(any())).thenReturn(0L);
    lenient().when(userRepository.countJoinedBetween(any(), any())).thenReturn(0L);
    lenient().when(userRepository.countWithdrawnBetween(any(), any())).thenReturn(0L);

    assertThat(adminUserService.getMonthlyStats(0)).hasSize(1);
    assertThat(adminUserService.getMonthlyStats(100)).hasSize(24);
  }
}
