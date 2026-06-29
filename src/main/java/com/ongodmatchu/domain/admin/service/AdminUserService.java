package com.ongodmatchu.domain.admin.service;

import com.ongodmatchu.domain.admin.dto.AdminUserDetailResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserHistoryResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserSummaryResponse;
import com.ongodmatchu.domain.admin.dto.AdminUserUpdateRequest;
import com.ongodmatchu.domain.admin.dto.AdminUserUpdateRequest.SuspensionUpdate;
import com.ongodmatchu.domain.admin.dto.MonthlyUserStatResponse;
import com.ongodmatchu.domain.admin.dto.SendNotificationRequest;
import com.ongodmatchu.domain.admin.entity.AdminUserChangeType;
import com.ongodmatchu.domain.admin.entity.AdminUserHistory;
import com.ongodmatchu.domain.admin.repository.AdminUserHistoryRepository;
import com.ongodmatchu.domain.notification.entity.UserNotification;
import com.ongodmatchu.domain.notification.repository.UserNotificationRepository;
import com.ongodmatchu.domain.quiz.repository.QuizAttemptRepository;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.quiz.repository.QuizRepository.QuizAggregateRow;
import com.ongodmatchu.domain.user.entity.Role;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.entity.UserStatus;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.domain.user.service.ProfileImageInitializer;
import com.ongodmatchu.domain.user.service.RandomNicknameGenerator;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.global.util.TimeFormat;
import com.ongodmatchu.infra.s3.S3Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

  private static final int MAX_PAGE_SIZE = 50;
  private static final int MAX_MONTHS = 24;

  private final UserRepository userRepository;
  private final QuizRepository quizRepository;
  private final QuizAttemptRepository quizAttemptRepository;
  private final UserNotificationRepository userNotificationRepository;
  private final AdminUserHistoryRepository adminUserHistoryRepository;
  private final ProfileImageInitializer profileImageInitializer;
  private final RandomNicknameGenerator randomNicknameGenerator;
  private final S3Service s3Service;

  @Value("${app.profile.default-image-url}")
  private String defaultProfileImageUrl;

  @Transactional(readOnly = true)
  public Page<AdminUserResponse> getUsers(
      String status, Role role, String query, Pageable pageable) {
    String normalizedQuery = (query == null || query.isBlank()) ? null : query.trim();
    Sort sort =
        pageable.getSort().isSorted()
            ? pageable.getSort()
            : Sort.by(Sort.Direction.DESC, "createdAt");
    Pageable capped =
        PageRequest.of(
            pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), sort);
    return userRepository
        .searchForAdmin(status, role, normalizedQuery, LocalDateTime.now(), capped)
        .map(u -> AdminUserResponse.from(u, resolveImageUrl(u)));
  }

  @Transactional(readOnly = true)
  public AdminUserDetailResponse getUserDetail(UUID targetPublicId) {
    User target = getManagedByPublicId(targetPublicId);
    QuizAggregateRow agg = quizRepository.aggregateByUserId(target.getId());
    long solvedCount = quizAttemptRepository.countByUserId(target.getId());
    Double avgSolveRate = quizAttemptRepository.avgSolveRateOf(target.getId());
    return AdminUserDetailResponse.of(
        target, resolveImageUrl(target), solvedCount, avgSolveRate, agg);
  }

  @Transactional(readOnly = true)
  public Page<AdminUserHistoryResponse> getHistories(UUID targetPublicId, Pageable pageable) {
    User target = getByPublicId(targetPublicId);
    Pageable capped =
        PageRequest.of(
            pageable.getPageNumber(),
            Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt"));
    return adminUserHistoryRepository
        .findByTargetUserIdOrderByCreatedAtDesc(target.getId(), capped)
        .map(AdminUserHistoryResponse::from);
  }

  @Transactional(readOnly = true)
  public AdminUserSummaryResponse getSummary() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime monthStart =
        YearMonth.from(LocalDate.now(TimeFormat.SERVER_OFFSET)).atDay(1).atStartOfDay();
    return new AdminUserSummaryResponse(
        userRepository.countActive(),
        userRepository.countActiveByRole(Role.OWNER),
        userRepository.countActiveByRole(Role.ADMIN),
        userRepository.countActiveByRole(Role.USER),
        userRepository.countSuspended(now),
        userRepository.countJoinedBetween(monthStart, now));
  }

  @Transactional(readOnly = true)
  public List<MonthlyUserStatResponse> getMonthlyStats(int months) {
    int capped = Math.min(Math.max(months, 1), MAX_MONTHS);
    YearMonth current = YearMonth.from(LocalDate.now(TimeFormat.SERVER_OFFSET));
    List<MonthlyUserStatResponse> result = new ArrayList<>();
    for (int i = capped - 1; i >= 0; i--) {
      YearMonth ym = current.minusMonths(i);
      LocalDateTime start = ym.atDay(1).atStartOfDay();
      LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
      result.add(
          new MonthlyUserStatResponse(
              ym.toString(),
              userRepository.countActiveAsOf(end),
              userRepository.countJoinedBetween(start, end),
              userRepository.countWithdrawnBetween(start, end)));
    }
    return result;
  }

  /** 유저 수정 화면의 "변경사항 저장" — 변경 항목 적용 + (선택) 알림 발송 + 관리 이력 1행 기록. */
  @Transactional
  public AdminUserResponse updateUser(
      Long actorId, UUID targetPublicId, AdminUserUpdateRequest request) {
    User actor = getById(actorId);
    User target = getManagedByPublicId(targetPublicId);
    List<Change> changes = new ArrayList<>();

    if (request.role() != null && request.role() != target.getRole()) {
      AdminAuthorization.assertCanChangeRole(actor, target, request.role());
      Role before = target.getRole();
      target.changeRole(request.role());
      changes.add(
          new Change(
              AdminUserChangeType.ROLE_CHANGE, before.name() + " → " + request.role().name()));
    }

    if (request.suspension() != null) {
      AdminAuthorization.assertCanSuspend(actor, target);
      applySuspension(target, request.suspension(), changes);
    }

    if (request.resetProfileImage() || request.resetNickname() || request.resetBio()) {
      AdminAuthorization.assertCanSuspend(actor, target);
    }
    if (request.resetNickname()) {
      String before = target.getNickname();
      String fresh = randomNicknameGenerator.generate();
      target.updateNickname(fresh);
      changes.add(new Change(AdminUserChangeType.NICKNAME_RESET, before + " → " + fresh));
    }
    if (request.resetProfileImage()) {
      deleteProfileImageObjects(target);
      target.clearProfileImage();
      profileImageInitializer.initialize(target);
      changes.add(new Change(AdminUserChangeType.PROFILE_IMAGE_RESET, "기본 이미지로 초기화"));
    }
    if (request.resetBio()) {
      target.updateBio(null);
      changes.add(new Change(AdminUserChangeType.BIO_RESET, "자기소개 초기화"));
    }

    UserNotification notification = null;
    if (request.notification() != null) {
      notification = createNotification(actor, target, request.notification());
    }

    recordHistory(actor, target, changes, notification);
    return AdminUserResponse.from(target);
  }

  /** 변경 없이 알림만 발송 (조회 리스트 "알림 보내기"). 알림만 보낸 이력으로 기록. */
  @Transactional
  public AdminUserResponse sendNotification(
      Long actorId, UUID targetPublicId, SendNotificationRequest request) {
    User actor = getById(actorId);
    User target = getManagedByPublicId(targetPublicId);
    UserNotification notification = createNotification(actor, target, request);
    recordHistory(actor, target, List.of(), notification);
    return AdminUserResponse.from(target);
  }

  // --- legacy: 기존 FE/테스트 호환용 N일 정지 경로. 신규 화면은 updateUser 의 종료일 기반 경로 사용. ---

  @Transactional
  public AdminUserResponse suspend(Long actorId, UUID targetPublicId, int days) {
    User actor = getById(actorId);
    User target = getByPublicId(targetPublicId);
    AdminAuthorization.assertCanSuspend(actor, target);
    target.suspendUntil(LocalDateTime.now().plusDays(days));
    return AdminUserResponse.from(target);
  }

  @Transactional
  public AdminUserResponse unsuspend(Long actorId, UUID targetPublicId) {
    User actor = getById(actorId);
    User target = getByPublicId(targetPublicId);
    AdminAuthorization.assertCanSuspend(actor, target);
    target.clearSuspension();
    return AdminUserResponse.from(target);
  }

  @Transactional
  public AdminUserResponse changeRole(Long actorId, UUID targetPublicId, Role newRole) {
    User actor = getById(actorId);
    User target = getByPublicId(targetPublicId);
    AdminAuthorization.assertCanChangeRole(actor, target, newRole);
    target.changeRole(newRole);
    return AdminUserResponse.from(target);
  }

  // --- helpers ---

  private void applySuspension(User target, SuspensionUpdate suspension, List<Change> changes) {
    boolean wasSuspended = target.isSuspended();
    if (suspension.suspend()) {
      if (suspension.until() == null) {
        throw new BusinessException(ErrorCode.INVALID_INPUT, "정지 종료일이 필요합니다.");
      }
      LocalDateTime until =
          suspension.until().withOffsetSameInstant(TimeFormat.SERVER_OFFSET).toLocalDateTime();
      if (!until.isAfter(LocalDateTime.now())) {
        throw new BusinessException(ErrorCode.INVALID_INPUT, "정지 종료일은 현재 이후여야 합니다.");
      }
      target.suspendUntil(until);
      String detail =
          wasSuspended
              ? "정지 기한 변경 (~" + until.toLocalDate() + ")"
              : "정상 → 정지 (~" + until.toLocalDate() + ")";
      changes.add(new Change(AdminUserChangeType.ACCOUNT_STATUS_CHANGE, detail));
    } else if (wasSuspended) {
      target.clearSuspension();
      changes.add(new Change(AdminUserChangeType.ACCOUNT_STATUS_CHANGE, "정지 → 정상"));
    }
  }

  private UserNotification createNotification(
      User actor, User target, SendNotificationRequest request) {
    if (target.getStatus() == UserStatus.WITHDRAWN) {
      throw new BusinessException(ErrorCode.ADMIN_TARGET_INVALID);
    }
    return userNotificationRepository.save(
        UserNotification.builder()
            .targetUser(target)
            .sender(actor)
            .type(request.type())
            .title(request.title().trim())
            .content(request.content().trim())
            .build());
  }

  private void recordHistory(
      User actor, User target, List<Change> changes, UserNotification notification) {
    AdminUserChangeType type;
    String detail;
    if (changes.isEmpty()) {
      if (notification == null) {
        return;
      }
      type = null;
      detail = null;
    } else if (changes.size() == 1) {
      type = changes.get(0).type();
      detail = changes.get(0).detail();
    } else {
      type = AdminUserChangeType.MULTIPLE;
      List<String> lines = new ArrayList<>();
      for (Change change : changes) {
        lines.add(
            change.detail() == null
                ? change.type().getLabel()
                : change.type().getLabel() + " — " + change.detail());
      }
      detail = String.join("\n", lines);
    }
    adminUserHistoryRepository.save(
        AdminUserHistory.builder()
            .actor(actor)
            .targetUser(target)
            .changeType(type)
            .detail(detail)
            .relatedNotification(notification)
            .build());
  }

  private void deleteProfileImageObjects(User user) {
    if (user.getProfileImageKey() != null) {
      s3Service.deleteQuietly(user.getProfileImageKey());
    }
    if (user.getOriginalProfileImageKey() != null) {
      s3Service.deleteQuietly(user.getOriginalProfileImageKey());
    }
  }

  private String resolveImageUrl(User user) {
    String key = user.getProfileImageKey();
    return key == null ? defaultProfileImageUrl : s3Service.generateViewUrl(key).viewUrl();
  }

  private User getById(Long id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }

  private User getByPublicId(UUID publicId) {
    return userRepository
        .findByPublicId(publicId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }

  /** 시스템 계정은 관리 대상이 아니므로 USER_NOT_FOUND 로 마스킹. */
  private User getManagedByPublicId(UUID publicId) {
    User user = getByPublicId(publicId);
    if (user.isSystem()) {
      throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    return user;
  }

  private record Change(AdminUserChangeType type, String detail) {}
}
