package com.ongodmatchu.domain.user.service;

import com.ongodmatchu.domain.auth.repository.RefreshTokenRepository;
import com.ongodmatchu.domain.auth.validation.PasswordValidator;
import com.ongodmatchu.domain.auth.validation.TermsPolicy;
import com.ongodmatchu.domain.quiz.service.QuizService;
import com.ongodmatchu.domain.user.dto.PasswordChangeRequest;
import com.ongodmatchu.domain.user.dto.PublicUserResponse;
import com.ongodmatchu.domain.user.dto.TermsAgreementRequest;
import com.ongodmatchu.domain.user.dto.UserResponse;
import com.ongodmatchu.domain.user.dto.UserUpdateRequest;
import com.ongodmatchu.domain.user.dto.WithdrawRequest;
import com.ongodmatchu.domain.user.entity.AdminAccount;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.entity.WithdrawalReasonRecord;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.domain.user.repository.WithdrawalReasonRepository;
import com.ongodmatchu.domain.user.validation.BioPolicy;
import com.ongodmatchu.domain.user.validation.NicknameNormalizer;
import com.ongodmatchu.domain.user.validation.NicknamePolicy;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.s3.PresignedUrlRequest;
import com.ongodmatchu.infra.s3.PresignedUrlResponse;
import com.ongodmatchu.infra.s3.S3Service;
import com.ongodmatchu.infra.s3.UploadPolicy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  static final String WITHDRAWAL_CONFIRMATION_PHRASE = "탈퇴하겠습니다.";

  private final UserRepository userRepository;
  private final NicknameNormalizer nicknameNormalizer;
  private final NicknamePolicy nicknamePolicy;
  private final BioPolicy bioPolicy;
  private final PasswordEncoder passwordEncoder;
  private final PasswordValidator passwordValidator;
  private final RefreshTokenRepository refreshTokenRepository;
  private final S3Service s3Service;
  private final ProfileImageGenerator profileImageGenerator;
  private final QuizService quizService;
  private final WithdrawalReasonRepository withdrawalReasonRepository;
  private final WithdrawalCodeService withdrawalCodeService;

  @Value("${app.profile.default-image-url}")
  private String defaultProfileImageUrl;

  @Transactional(readOnly = true)
  public UserResponse getMe(Long userId) {
    User user = findUserById(userId);
    return toResponse(user);
  }

  /**
   * 약관 미동의 사용자가 현재 버전 약관에 동의 처리. 이미 동의된 사용자가 호출해도 idempotent. 동의 후 갱신된 {@link UserResponse} 를 반환해
   * FE 가 별도 GET /me 없이 store 동기화 가능.
   */
  @Transactional
  public UserResponse agreeToCurrentTerms(Long userId, TermsAgreementRequest request) {
    User user = findUserById(userId);
    user.agreeToTerms(
        TermsPolicy.CURRENT_TERMS_VERSION,
        TermsPolicy.CURRENT_PRIVACY_VERSION,
        request != null && request.marketingOptIn());
    return toResponse(user);
  }

  /** 본인이거나 공개 프로필이면 {@link UserResponse}, 비공개면 {@link PublicUserResponse} 를 반환한다. */
  @Transactional(readOnly = true)
  public Object getProfile(UUID publicId, Long viewerUserId) {
    User user =
        userRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    boolean isOwner = viewerUserId != null && viewerUserId.equals(user.getId());
    if (!user.isProfilePublic() && !isOwner) {
      return PublicUserResponse.from(user, resolveImageUrl(user));
    }
    return toResponse(user);
  }

  @Transactional
  public UserResponse updateMe(Long userId, UserUpdateRequest request) {
    User user = findUserById(userId);

    if (request.nickname() != null) {
      String nickname = nicknameNormalizer.normalize(request.nickname());
      nicknamePolicy.enforce(nickname);
      if (!user.getNickname().equals(nickname) && userRepository.existsByNickname(nickname)) {
        throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
      }
      user.updateNickname(nickname);
    }

    if (request.bio() != null) {
      String bio = bioPolicy.normalize(request.bio());
      bioPolicy.enforce(bio);
      user.updateBio(bio);
    }

    if (request.isProfilePublic() != null) {
      user.updateProfilePublic(request.isProfilePublic());
    }

    try {
      userRepository.flush();
    } catch (DataIntegrityViolationException e) {
      String message = e.getMostSpecificCause().getMessage();
      if (message != null && message.toLowerCase().contains("nickname")) {
        throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
      }
      throw e;
    }
    return toResponse(user);
  }

  @Transactional
  public void changePassword(Long userId, PasswordChangeRequest request) {
    User user = findUserById(userId);

    if (user.getProvider() != AuthProvider.LOCAL || user.getPassword() == null) {
      throw new BusinessException(ErrorCode.OAUTH_USER_NO_PASSWORD);
    }
    if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
    }

    passwordValidator.validate(request.newPassword(), user.getEmail(), user.getNickname());

    user.updatePassword(passwordEncoder.encode(request.newPassword()));
    refreshTokenRepository.deleteByUserId(userId);
  }

  @Transactional
  public PresignedUrlResponse issueProfileImageUploadUrl(Long userId, PresignedUrlRequest request) {
    return s3Service.generateProfileImageUploadUrl(userId, request);
  }

  @Transactional
  public UserResponse applyProfileImage(Long userId, String key) {
    if (key == null || !key.startsWith(UploadPolicy.PROFILE_IMAGES_PREFIX + "/")) {
      throw new BusinessException(ErrorCode.INVALID_UPLOAD_KEY);
    }
    User user = findUserById(userId);
    s3Service.completeUpload(userId, key);

    String previousKey = user.getProfileImageKey();
    user.updateProfileImageKey(key);
    if (previousKey != null && !previousKey.equals(key)) {
      s3Service.deleteQuietly(previousKey);
    }
    return toResponse(user);
  }

  /** "기본 이미지" 버튼 — 호출마다 새 랜덤 색 SVG 를 생성해 적용한다. 이전 키는 best-effort 삭제. */
  @Transactional
  public UserResponse regenerateDefaultProfileImage(Long userId) {
    User user = findUserById(userId);
    String previousKey = user.getProfileImageKey();
    String newKey =
        UploadPolicy.PROFILE_IMAGES_PREFIX
            + "/"
            + user.getPublicId()
            + "/"
            + UUID.randomUUID()
            + ".svg";
    byte[] body = profileImageGenerator.generateSvg(user.getNickname());
    s3Service.putObject(newKey, body, ProfileImageGenerator.CONTENT_TYPE);
    user.updateProfileImageKey(newKey);
    if (previousKey != null) {
      s3Service.deleteQuietly(previousKey);
    }
    return toResponse(user);
  }

  /**
   * 회원탈퇴 — soft delete + 본인 퀴즈 처리(이전/삭제) + 탈퇴 이유 익명 저장.
   *
   * <ol>
   *   <li>모달 "탈퇴하겠습니다." 문구 일치 검증 → 미일치 시 {@link ErrorCode#INVALID_WITHDRAWAL_CONFIRMATION}
   *   <li>이메일 인증 코드 검증 + 소비 (LOCAL/OAuth 무관 동일)
   *   <li>{@code deleteOwnQuizzes=true} 면 본인 퀴즈+연관 데이터 일괄 삭제 / {@code false}(default) 면 시스템 관리자
   *       계정으로 작성자 일괄 이전
   *   <li>탈퇴 이유(주관식) 익명 통계 저장 (reasonText 가 비어있지 않을 때)
   *   <li>email/nickname 익명화 + isActive=false + deletedAt 기록 + RT 전체 무효화 + 프로필 이미지 best-effort 삭제
   * </ol>
   */
  @Transactional
  public void withdraw(Long userId, WithdrawRequest request) {
    User user = findUserById(userId);

    String confirmation = request == null ? null : request.confirmationPhrase();
    if (!WITHDRAWAL_CONFIRMATION_PHRASE.equals(confirmation)) {
      throw new BusinessException(ErrorCode.INVALID_WITHDRAWAL_CONFIRMATION);
    }
    withdrawalCodeService.verifyAndConsume(userId, request.verificationCode());

    if (request.shouldDeleteOwnQuizzes()) {
      quizService.deleteAllByUserId(userId);
    } else {
      User admin =
          userRepository
              .findByPublicId(AdminAccount.PUBLIC_ID)
              .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
      quizService.transferOwnershipToAdmin(userId, admin.getId());
    }

    saveWithdrawalReason(request.reasonText());

    String anonymizedKey = "deleted_" + user.getPublicId();
    String previousImageKey = user.getProfileImageKey();
    user.withdraw(anonymizedKey + "@deleted.local", anonymizedKey);

    refreshTokenRepository.deleteByUserId(userId);
    if (previousImageKey != null) {
      s3Service.deleteQuietly(previousImageKey);
    }
  }

  private void saveWithdrawalReason(String reasonText) {
    if (reasonText == null) {
      return;
    }
    String trimmed = reasonText.trim();
    if (trimmed.isEmpty()) {
      return;
    }
    withdrawalReasonRepository.save(WithdrawalReasonRecord.builder().reasonText(trimmed).build());
  }

  @Transactional
  public UserResponse deleteProfileImage(Long userId) {
    User user = findUserById(userId);
    String previousKey = user.getProfileImageKey();
    if (previousKey != null) {
      user.clearProfileImage();
      s3Service.deleteQuietly(previousKey);
    }
    return toResponse(user);
  }

  private UserResponse toResponse(User user) {
    return UserResponse.from(user, resolveImageUrl(user), calcActiveDays(user.getCreatedAt()));
  }

  private String resolveImageUrl(User user) {
    String key = user.getProfileImageKey();
    return key == null ? defaultProfileImageUrl : s3Service.generateViewUrl(key).viewUrl();
  }

  private long calcActiveDays(LocalDateTime createdAt) {
    if (createdAt == null) {
      return 0L;
    }
    long days = Duration.between(createdAt, LocalDateTime.now()).toDays();
    return Math.max(days, 0L);
  }

  private User findUserById(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }
}
