package com.ongodmatchu.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.auth.repository.RefreshTokenRepository;
import com.ongodmatchu.domain.auth.validation.PasswordValidator;
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
import com.ongodmatchu.infra.s3.ViewUrlResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @InjectMocks private UserService userService;
  @Mock private UserRepository userRepository;
  @Mock private NicknameNormalizer nicknameNormalizer;
  @Mock private NicknamePolicy nicknamePolicy;
  @Mock private BioPolicy bioPolicy;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private PasswordValidator passwordValidator;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private S3Service s3Service;
  @Mock private ProfileImageGenerator profileImageGenerator;
  @Mock private QuizService quizService;
  @Mock private WithdrawalReasonRepository withdrawalReasonRepository;
  @Mock private WithdrawalCodeService withdrawalCodeService;

  private static final String DEFAULT_IMAGE_URL = "https://cdn.example.com/default.png";
  private static final String VIEW_URL = "https://cdn.example.com/presigned-view-url";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(userService, "defaultProfileImageUrl", DEFAULT_IMAGE_URL);
  }

  // ============ 헬퍼 ============

  private User buildLocalUser(Long id, String nickname) {
    User user =
        User.builder()
            .email("user@example.com")
            .nickname(nickname)
            .password("hashed")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", id);
    ReflectionTestUtils.setField(user, "publicId", UUID.randomUUID());
    return user;
  }

  private User buildOAuthUser(Long id, String nickname) {
    User user =
        User.builder()
            .email("oauth@example.com")
            .nickname(nickname)
            .provider(AuthProvider.GOOGLE)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", id);
    ReflectionTestUtils.setField(user, "publicId", UUID.randomUUID());
    return user;
  }

  private ViewUrlResponse viewUrlResponse(String key) {
    return new ViewUrlResponse(VIEW_URL, key, 3600L, Instant.now().plusSeconds(3600));
  }

  // ============ getMe ============

  @Test
  @DisplayName("getMe_이미지키없음_defaultProfileImageUrl반환")
  void getMe_noProfileImageKey_returnsDefaultUrl() {
    User user = buildLocalUser(1L, "홍길동");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    UserResponse result = userService.getMe(1L);

    assertThat(result.profileImageUrl()).isEqualTo(DEFAULT_IMAGE_URL);
    assertThat(result.nickname()).isEqualTo("홍길동");
    then(s3Service).should(never()).generateViewUrl(anyString());
  }

  @Test
  @DisplayName("getMe_이미지키있음_presigned_view_url반환")
  void getMe_hasProfileImageKey_returnsPresignedViewUrl() {
    User user = buildLocalUser(1L, "홍길동");
    user.updateProfileImageKey("profile-images/uuid/photo.jpg");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(s3Service.generateViewUrl("profile-images/uuid/photo.jpg"))
        .willReturn(viewUrlResponse("profile-images/uuid/photo.jpg"));

    UserResponse result = userService.getMe(1L);

    assertThat(result.profileImageUrl()).isEqualTo(VIEW_URL);
  }

  @Test
  @DisplayName("getMe_사용자미존재_USER_NOT_FOUND예외")
  void getMe_userNotFound_throwsException() {
    given(userRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getMe(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  // ============ getProfile ============

  @Test
  @DisplayName("getProfile_공개프로필_외부뷰어_UserResponse반환")
  void getProfile_publicProfile_externalViewer_returnsUserResponse() {
    UUID publicId = UUID.randomUUID();
    User user = buildLocalUser(1L, "공개유저");
    ReflectionTestUtils.setField(user, "publicId", publicId);
    given(userRepository.findByPublicId(publicId)).willReturn(Optional.of(user));

    Object result = userService.getProfile(publicId, 99L);

    assertThat(result).isInstanceOf(UserResponse.class);
  }

  @Test
  @DisplayName("getProfile_공개프로필_비로그인_UserResponse반환")
  void getProfile_publicProfile_anonymousViewer_returnsUserResponse() {
    UUID publicId = UUID.randomUUID();
    User user = buildLocalUser(1L, "공개유저");
    ReflectionTestUtils.setField(user, "publicId", publicId);
    given(userRepository.findByPublicId(publicId)).willReturn(Optional.of(user));

    Object result = userService.getProfile(publicId, null);

    assertThat(result).isInstanceOf(UserResponse.class);
  }

  @Test
  @DisplayName("getProfile_비공개프로필_본인조회_UserResponse반환")
  void getProfile_privateProfile_ownerViewer_returnsUserResponse() {
    UUID publicId = UUID.randomUUID();
    User user = buildLocalUser(1L, "비공개본인");
    ReflectionTestUtils.setField(user, "publicId", publicId);
    user.updateProfilePublic(false);
    given(userRepository.findByPublicId(publicId)).willReturn(Optional.of(user));

    Object result = userService.getProfile(publicId, 1L);

    assertThat(result).isInstanceOf(UserResponse.class);
  }

  @Test
  @DisplayName("getProfile_비공개프로필_외부뷰어_PublicUserResponse반환")
  void getProfile_privateProfile_externalViewer_returnsPublicUserResponse() {
    UUID publicId = UUID.randomUUID();
    User user = buildLocalUser(1L, "비공개유저");
    ReflectionTestUtils.setField(user, "publicId", publicId);
    user.updateProfilePublic(false);
    given(userRepository.findByPublicId(publicId)).willReturn(Optional.of(user));

    Object result = userService.getProfile(publicId, 99L);

    assertThat(result).isInstanceOf(PublicUserResponse.class);
    PublicUserResponse publicResponse = (PublicUserResponse) result;
    assertThat(publicResponse.isProfilePublic()).isFalse();
  }

  @Test
  @DisplayName("getProfile_비공개프로필_비로그인_PublicUserResponse반환")
  void getProfile_privateProfile_anonymousViewer_returnsPublicUserResponse() {
    UUID publicId = UUID.randomUUID();
    User user = buildLocalUser(1L, "비공개유저");
    ReflectionTestUtils.setField(user, "publicId", publicId);
    user.updateProfilePublic(false);
    given(userRepository.findByPublicId(publicId)).willReturn(Optional.of(user));

    Object result = userService.getProfile(publicId, null);

    assertThat(result).isInstanceOf(PublicUserResponse.class);
  }

  @Test
  @DisplayName("getProfile_사용자미존재_USER_NOT_FOUND예외")
  void getProfile_userNotFound_throwsException() {
    UUID unknownId = UUID.randomUUID();
    given(userRepository.findByPublicId(unknownId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getProfile(unknownId, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  // ============ updateMe ============

  @Test
  @DisplayName("updateMe_닉네임변경_정상처리")
  void updateMe_nicknameChange_success() {
    User user = buildLocalUser(1L, "기존닉네임");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(nicknameNormalizer.normalize("새닉네임")).willReturn("새닉네임");
    given(userRepository.existsByNickname("새닉네임")).willReturn(false);

    UserResponse result = userService.updateMe(1L, new UserUpdateRequest("새닉네임", null, null));

    assertThat(result.nickname()).isEqualTo("새닉네임");
    then(nicknamePolicy).should().enforce("새닉네임");
  }

  @Test
  @DisplayName("updateMe_본인닉네임동일_중복체크스킵")
  void updateMe_sameNickname_skipsDuplicateCheck() {
    User user = buildLocalUser(1L, "동일닉네임");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(nicknameNormalizer.normalize("동일닉네임")).willReturn("동일닉네임");

    userService.updateMe(1L, new UserUpdateRequest("동일닉네임", null, null));

    then(userRepository).should(never()).existsByNickname(anyString());
  }

  @Test
  @DisplayName("updateMe_닉네임중복_NICKNAME_ALREADY_EXISTS예외")
  void updateMe_duplicateNickname_throwsException() {
    User user = buildLocalUser(1L, "기존닉네임");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(nicknameNormalizer.normalize("중복닉네임")).willReturn("중복닉네임");
    given(userRepository.existsByNickname("중복닉네임")).willReturn(true);

    assertThatThrownBy(() -> userService.updateMe(1L, new UserUpdateRequest("중복닉네임", null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS);
  }

  @Test
  @DisplayName("updateMe_DataIntegrityViolation닉네임포함_NICKNAME_ALREADY_EXISTS예외")
  void updateMe_dataIntegrityViolationWithNickname_convertsException() {
    User user = buildLocalUser(1L, "기존닉네임");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(nicknameNormalizer.normalize("레이스닉네임")).willReturn("레이스닉네임");
    given(userRepository.existsByNickname("레이스닉네임")).willReturn(false);
    RuntimeException cause =
        new RuntimeException("ERROR: duplicate key violates constraint nickname");
    DataIntegrityViolationException dive =
        new DataIntegrityViolationException("constraint violation", cause);
    willThrow(dive).given(userRepository).flush();

    assertThatThrownBy(() -> userService.updateMe(1L, new UserUpdateRequest("레이스닉네임", null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS);
  }

  @Test
  @DisplayName("updateMe_DataIntegrityViolation닉네임미포함_재throw")
  void updateMe_dataIntegrityViolationWithoutNickname_rethrows() {
    User user = buildLocalUser(1L, "기존닉네임");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(nicknameNormalizer.normalize("새닉네임")).willReturn("새닉네임");
    given(userRepository.existsByNickname("새닉네임")).willReturn(false);
    RuntimeException cause = new RuntimeException("ERROR: duplicate key violates constraint email");
    DataIntegrityViolationException dive =
        new DataIntegrityViolationException("constraint violation", cause);
    willThrow(dive).given(userRepository).flush();

    assertThatThrownBy(() -> userService.updateMe(1L, new UserUpdateRequest("새닉네임", null, null)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("updateMe_bio변경_정상처리")
  void updateMe_bioChange_success() {
    User user = buildLocalUser(1L, "닉네임");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(bioPolicy.normalize("안녕하세요")).willReturn("안녕하세요");

    userService.updateMe(1L, new UserUpdateRequest(null, "안녕하세요", null));

    then(bioPolicy).should().normalize("안녕하세요");
    then(bioPolicy).should().enforce("안녕하세요");
  }

  @Test
  @DisplayName("updateMe_isProfilePublic변경_정상처리")
  void updateMe_profilePublicToggle_success() {
    User user = buildLocalUser(1L, "닉네임");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    UserResponse result = userService.updateMe(1L, new UserUpdateRequest(null, null, false));

    assertThat(result.isProfilePublic()).isFalse();
  }

  @Test
  @DisplayName("updateMe_모든필드null_변경없음")
  void updateMe_allFieldsNull_noChange() {
    User user = buildLocalUser(1L, "기존닉네임");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    UserResponse result = userService.updateMe(1L, new UserUpdateRequest(null, null, null));

    assertThat(result.nickname()).isEqualTo("기존닉네임");
    then(nicknameNormalizer).should(never()).normalize(anyString());
    then(bioPolicy).should(never()).normalize(anyString());
  }

  @Test
  @DisplayName("updateMe_사용자미존재_USER_NOT_FOUND예외")
  void updateMe_userNotFound_throwsException() {
    given(userRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.updateMe(99L, new UserUpdateRequest("닉네임", null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  // ============ changePassword ============

  @Test
  @DisplayName("changePassword_OAuth유저_OAUTH_USER_NO_PASSWORD예외")
  void changePassword_oauthUser_throwsException() {
    User oauthUser = buildOAuthUser(1L, "소셜유저");
    given(userRepository.findById(1L)).willReturn(Optional.of(oauthUser));

    assertThatThrownBy(
            () ->
                userService.changePassword(
                    1L, new PasswordChangeRequest("current", "newPassword123!")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OAUTH_USER_NO_PASSWORD);

    then(refreshTokenRepository).should(never()).deleteByUserId(any());
  }

  @Test
  @DisplayName("changePassword_password필드null_OAUTH_USER_NO_PASSWORD예외")
  void changePassword_nullPassword_throwsException() {
    User user =
        User.builder()
            .email("user@example.com")
            .nickname("유저")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    assertThatThrownBy(
            () ->
                userService.changePassword(
                    1L, new PasswordChangeRequest("current", "newPassword123!")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OAUTH_USER_NO_PASSWORD);
  }

  @Test
  @DisplayName("changePassword_현재비밀번호불일치_INVALID_CURRENT_PASSWORD예외")
  void changePassword_wrongCurrentPassword_throwsException() {
    User user = buildLocalUser(1L, "유저");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

    assertThatThrownBy(
            () -> userService.changePassword(1L, new PasswordChangeRequest("wrong", "newPass123!")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD);

    then(refreshTokenRepository).should(never()).deleteByUserId(any());
  }

  @Test
  @DisplayName("changePassword_정상_비밀번호업데이트_리프레시토큰삭제")
  void changePassword_success_updatesPasswordAndDeletesTokens() {
    User user = buildLocalUser(1L, "유저");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(passwordEncoder.matches("currentPass", "hashed")).willReturn(true);
    given(passwordEncoder.encode("newPassword123!")).willReturn("newHashed");
    willDoNothing().given(passwordValidator).validate(anyString(), anyString(), anyString());

    userService.changePassword(1L, new PasswordChangeRequest("currentPass", "newPassword123!"));

    assertThat(user.getPassword()).isEqualTo("newHashed");
    then(refreshTokenRepository).should().deleteByUserId(1L);
  }

  @Test
  @DisplayName("changePassword_사용자미존재_USER_NOT_FOUND예외")
  void changePassword_userNotFound_throwsException() {
    given(userRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                userService.changePassword(
                    99L, new PasswordChangeRequest("current", "newPassword123!")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  // ============ issueProfileImageUploadUrl ============

  @Test
  @DisplayName("issueProfileImageUploadUrl_S3Service위임검증")
  void issueProfileImageUploadUrl_delegatesToS3Service() {
    PresignedUrlRequest request = new PresignedUrlRequest("photo.jpg", "image/jpeg", 1024L);
    PresignedUrlResponse expected =
        new PresignedUrlResponse("https://s3.presigned", "key", 600L, java.util.Map.of());
    given(s3Service.generateProfileImageUploadUrl(1L, request)).willReturn(expected);

    PresignedUrlResponse result = userService.issueProfileImageUploadUrl(1L, request);

    assertThat(result).isEqualTo(expected);
    then(s3Service).should().generateProfileImageUploadUrl(1L, request);
  }

  // ============ applyProfileImage ============

  @Test
  @DisplayName("applyProfileImage_유효한키_이미지키갱신")
  void applyProfileImage_validKey_updatesProfileImageKey() {
    User user = buildLocalUser(1L, "유저");
    String key = UploadPolicy.PROFILE_IMAGES_PREFIX + "/uuid/photo.jpg";
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(s3Service.generateViewUrl(key)).willReturn(viewUrlResponse(key));

    userService.applyProfileImage(1L, key);

    assertThat(user.getProfileImageKey()).isEqualTo(key);
    then(s3Service).should().completeUpload(1L, key);
  }

  @Test
  @DisplayName("applyProfileImage_이전키와다름_이전키삭제")
  void applyProfileImage_differentPreviousKey_deletesPreviousKey() {
    User user = buildLocalUser(1L, "유저");
    String oldKey = UploadPolicy.PROFILE_IMAGES_PREFIX + "/uuid/old.jpg";
    String newKey = UploadPolicy.PROFILE_IMAGES_PREFIX + "/uuid/new.jpg";
    user.updateProfileImageKey(oldKey);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(s3Service.generateViewUrl(newKey)).willReturn(viewUrlResponse(newKey));

    userService.applyProfileImage(1L, newKey);

    then(s3Service).should().deleteQuietly(oldKey);
  }

  @Test
  @DisplayName("applyProfileImage_이전키없음_deleteQuietly미호출")
  void applyProfileImage_noPreviousKey_doesNotDeleteAnything() {
    User user = buildLocalUser(1L, "유저");
    String key = UploadPolicy.PROFILE_IMAGES_PREFIX + "/uuid/photo.jpg";
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(s3Service.generateViewUrl(key)).willReturn(viewUrlResponse(key));

    userService.applyProfileImage(1L, key);

    then(s3Service).should(never()).deleteQuietly(anyString());
  }

  @Test
  @DisplayName("applyProfileImage_이전키와동일_deleteQuietly미호출")
  void applyProfileImage_sameKeyAsExisting_doesNotDeleteAnything() {
    User user = buildLocalUser(1L, "유저");
    String key = UploadPolicy.PROFILE_IMAGES_PREFIX + "/uuid/same.jpg";
    user.updateProfileImageKey(key);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(s3Service.generateViewUrl(key)).willReturn(viewUrlResponse(key));

    userService.applyProfileImage(1L, key);

    then(s3Service).should(never()).deleteQuietly(anyString());
  }

  @Test
  @DisplayName("applyProfileImage_잘못된prefix_INVALID_UPLOAD_KEY예외")
  void applyProfileImage_invalidKeyPrefix_throwsException() {
    assertThatThrownBy(() -> userService.applyProfileImage(1L, "quiz-images/uuid/photo.jpg"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_UPLOAD_KEY);
  }

  @Test
  @DisplayName("applyProfileImage_null키_INVALID_UPLOAD_KEY예외")
  void applyProfileImage_nullKey_throwsException() {
    assertThatThrownBy(() -> userService.applyProfileImage(1L, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_UPLOAD_KEY);
  }

  // ============ deleteProfileImage ============

  @Test
  @DisplayName("deleteProfileImage_이미지키있음_clearAndDeleteQuietly호출")
  void deleteProfileImage_hasKey_clearsAndDeletes() {
    User user = buildLocalUser(1L, "유저");
    user.updateProfileImageKey("profile-images/uuid/photo.jpg");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    userService.deleteProfileImage(1L);

    assertThat(user.getProfileImageKey()).isNull();
    then(s3Service).should().deleteQuietly("profile-images/uuid/photo.jpg");
  }

  @Test
  @DisplayName("deleteProfileImage_이미지키없음_S3호출없음_no_op")
  void deleteProfileImage_noKey_doesNothing() {
    User user = buildLocalUser(1L, "유저");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    userService.deleteProfileImage(1L);

    then(s3Service).should(never()).deleteQuietly(anyString());
  }

  @Test
  @DisplayName("deleteProfileImage_사용자미존재_USER_NOT_FOUND예외")
  void deleteProfileImage_userNotFound_throwsException() {
    given(userRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.deleteProfileImage(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  // ============ regenerateDefaultProfileImage ============

  @Test
  @DisplayName("regenerateDefaultProfileImage_새SVG생성후S3업로드_프로필키갱신")
  void regenerateDefaultProfileImage_uploadsNewSvgAndUpdatesKey() {
    User user = buildLocalUser(1L, "유저");
    byte[] svgBytes = "<svg/>".getBytes();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(profileImageGenerator.generateSvg("유저")).willReturn(svgBytes);
    given(s3Service.generateViewUrl(anyString()))
        .willAnswer(inv -> viewUrlResponse(inv.getArgument(0)));

    UserResponse result = userService.regenerateDefaultProfileImage(1L);

    assertThat(user.getProfileImageKey())
        .isNotNull()
        .startsWith(UploadPolicy.PROFILE_IMAGES_PREFIX + "/" + user.getPublicId() + "/")
        .endsWith(".svg");
    then(s3Service)
        .should()
        .putObject(
            eq(user.getProfileImageKey()), eq(svgBytes), eq(ProfileImageGenerator.CONTENT_TYPE));
    assertThat(result.profileImageUrl()).isEqualTo(VIEW_URL);
  }

  @Test
  @DisplayName("regenerateDefaultProfileImage_이전키있음_이전키_deleteQuietly호출")
  void regenerateDefaultProfileImage_hasPreviousKey_deletesPrevious() {
    User user = buildLocalUser(1L, "유저");
    String oldKey = UploadPolicy.PROFILE_IMAGES_PREFIX + "/uuid/old.svg";
    user.updateProfileImageKey(oldKey);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(profileImageGenerator.generateSvg("유저")).willReturn("<svg/>".getBytes());
    given(s3Service.generateViewUrl(anyString()))
        .willAnswer(inv -> viewUrlResponse(inv.getArgument(0)));

    userService.regenerateDefaultProfileImage(1L);

    then(s3Service).should().deleteQuietly(oldKey);
  }

  @Test
  @DisplayName("regenerateDefaultProfileImage_이전키없음_deleteQuietly미호출")
  void regenerateDefaultProfileImage_noPreviousKey_skipsDelete() {
    User user = buildLocalUser(1L, "유저");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(profileImageGenerator.generateSvg("유저")).willReturn("<svg/>".getBytes());
    given(s3Service.generateViewUrl(anyString()))
        .willAnswer(inv -> viewUrlResponse(inv.getArgument(0)));

    userService.regenerateDefaultProfileImage(1L);

    then(s3Service).should(never()).deleteQuietly(anyString());
  }

  @Test
  @DisplayName("regenerateDefaultProfileImage_사용자미존재_USER_NOT_FOUND예외")
  void regenerateDefaultProfileImage_userNotFound_throwsException() {
    given(userRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.regenerateDefaultProfileImage(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  // ============ withdraw ============

  private static final String PHRASE = "탈퇴하겠습니다.";
  private static final String CODE = "123456";

  private User buildAdmin() {
    User admin =
        User.builder()
            .email("admin@system.local")
            .nickname("관리자")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(admin, "id", 999L);
    ReflectionTestUtils.setField(admin, "publicId", AdminAccount.PUBLIC_ID);
    ReflectionTestUtils.setField(admin, "isSystem", true);
    return admin;
  }

  private WithdrawRequest req(String code, String phrase, Boolean deleteOwn) {
    return new WithdrawRequest(code, phrase, deleteOwn, null);
  }

  private WithdrawRequest req(String code, String phrase, Boolean deleteOwn, String reasonText) {
    return new WithdrawRequest(code, phrase, deleteOwn, reasonText);
  }

  @Test
  @DisplayName("withdraw_정상_관리자이전_익명화_RT삭제_프로필이미지삭제_코드소비")
  void withdraw_success_transfersOwnershipAndAnonymizes() {
    User user = buildLocalUser(1L, "유저");
    UUID publicId = user.getPublicId();
    user.updateProfileImageKey("profile-images/uuid/photo.jpg");
    User admin = buildAdmin();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(userRepository.findByPublicId(AdminAccount.PUBLIC_ID)).willReturn(Optional.of(admin));

    userService.withdraw(1L, req(CODE, PHRASE, false));

    then(withdrawalCodeService).should().verifyAndConsume(1L, CODE);
    assertThat(user.isActive()).isFalse();
    assertThat(user.getDeletedAt()).isNotNull();
    assertThat(user.getEmail()).isEqualTo("deleted_" + publicId + "@deleted.local");
    assertThat(user.getNickname()).isEqualTo("deleted_" + publicId);
    assertThat(user.getPassword()).isNull();
    assertThat(user.getProfileImageKey()).isNull();
    assertThat(user.isProfilePublic()).isFalse();
    then(quizService).should().transferOwnershipToAdmin(1L, 999L);
    then(quizService).should(never()).deleteAllByUserId(any());
    then(refreshTokenRepository).should().deleteByUserId(1L);
    then(s3Service).should().deleteQuietly("profile-images/uuid/photo.jpg");
  }

  @Test
  @DisplayName("withdraw_deleteOwnQuizzes_true_본인퀴즈일괄삭제_관리자이전없음")
  void withdraw_deleteOwnQuizzes_callsDeleteAll() {
    User user = buildLocalUser(1L, "유저");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    userService.withdraw(1L, req(CODE, PHRASE, true));

    then(quizService).should().deleteAllByUserId(1L);
    then(quizService).should(never()).transferOwnershipToAdmin(any(), any());
    then(userRepository).should(never()).findByPublicId(any());
  }

  @Test
  @DisplayName("withdraw_확인문구_미일치_INVALID_WITHDRAWAL_CONFIRMATION예외_코드검증도호출안됨")
  void withdraw_wrongConfirmationPhrase_throwsException() {
    User user = buildLocalUser(1L, "유저");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    assertThatThrownBy(() -> userService.withdraw(1L, req(CODE, "탈퇴할게요", false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_WITHDRAWAL_CONFIRMATION);

    assertThat(user.isActive()).isTrue();
    then(withdrawalCodeService).shouldHaveNoInteractions();
    then(quizService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("withdraw_request_null_INVALID_WITHDRAWAL_CONFIRMATION예외")
  void withdraw_nullRequest_throwsConfirmation() {
    User user = buildLocalUser(1L, "유저");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    assertThatThrownBy(() -> userService.withdraw(1L, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_WITHDRAWAL_CONFIRMATION);
    then(withdrawalCodeService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("withdraw_코드미일치_INVALID_VERIFICATION_CODE예외_확인문구통과후")
  void withdraw_invalidCode_throwsException() {
    User user = buildLocalUser(1L, "유저");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    org.mockito.BDDMockito.willThrow(new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE))
        .given(withdrawalCodeService)
        .verifyAndConsume(1L, "wrong");

    assertThatThrownBy(() -> userService.withdraw(1L, req("wrong", PHRASE, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE);

    assertThat(user.isActive()).isTrue();
    then(quizService).shouldHaveNoInteractions();
    then(refreshTokenRepository).should(never()).deleteByUserId(any());
  }

  @Test
  @DisplayName("withdraw_코드만료_VERIFICATION_CODE_EXPIRED예외")
  void withdraw_expiredCode_throwsException() {
    User user = buildLocalUser(1L, "유저");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    org.mockito.BDDMockito.willThrow(new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED))
        .given(withdrawalCodeService)
        .verifyAndConsume(1L, CODE);

    assertThatThrownBy(() -> userService.withdraw(1L, req(CODE, PHRASE, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED);
    assertThat(user.isActive()).isTrue();
  }

  @Test
  @DisplayName("withdraw_OAuth_LOCAL_분기없이_코드만으로_정상")
  void withdraw_oauth_codeOnly_success() {
    User user = buildOAuthUser(1L, "소셜유저");
    UUID publicId = user.getPublicId();
    User admin = buildAdmin();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(userRepository.findByPublicId(AdminAccount.PUBLIC_ID)).willReturn(Optional.of(admin));

    userService.withdraw(1L, req(CODE, PHRASE, false));

    assertThat(user.isActive()).isFalse();
    assertThat(user.getNickname()).isEqualTo("deleted_" + publicId);
    then(passwordEncoder).shouldHaveNoInteractions();
    then(quizService).should().transferOwnershipToAdmin(1L, 999L);
    then(refreshTokenRepository).should().deleteByUserId(1L);
  }

  @Test
  @DisplayName("withdraw_프로필이미지없음_S3_deleteQuietly_미호출")
  void withdraw_noProfileImage_skipsS3Delete() {
    User user = buildOAuthUser(1L, "소셜유저");
    User admin = buildAdmin();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(userRepository.findByPublicId(AdminAccount.PUBLIC_ID)).willReturn(Optional.of(admin));

    userService.withdraw(1L, req(CODE, PHRASE, false));

    then(s3Service).should(never()).deleteQuietly(anyString());
  }

  @Test
  @DisplayName("withdraw_사용자미존재_USER_NOT_FOUND예외")
  void withdraw_userNotFound_throwsException() {
    given(userRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.withdraw(99L, req(CODE, PHRASE, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("withdraw_관리자계정시드없음_USER_NOT_FOUND예외")
  void withdraw_adminMissing_throwsUserNotFound() {
    User user = buildOAuthUser(1L, "소셜유저");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(userRepository.findByPublicId(AdminAccount.PUBLIC_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.withdraw(1L, req(CODE, PHRASE, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);

    assertThat(user.isActive()).isTrue();
    then(refreshTokenRepository).should(never()).deleteByUserId(any());
  }

  @Test
  @DisplayName("withdraw_reasonText_있음_trim후_익명저장")
  void withdraw_withReasonText_savesTrimmed() {
    User user = buildOAuthUser(1L, "소셜유저");
    User admin = buildAdmin();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(userRepository.findByPublicId(AdminAccount.PUBLIC_ID)).willReturn(Optional.of(admin));

    userService.withdraw(1L, req(CODE, PHRASE, false, "  시간이 부족해서요  "));

    org.mockito.ArgumentCaptor<WithdrawalReasonRecord> captor =
        org.mockito.ArgumentCaptor.forClass(WithdrawalReasonRecord.class);
    then(withdrawalReasonRepository).should().save(captor.capture());
    assertThat(captor.getValue().getReasonText()).isEqualTo("시간이 부족해서요");
  }

  @Test
  @DisplayName("withdraw_reasonText_null_저장안됨")
  void withdraw_nullReasonText_skipsSave() {
    User user = buildOAuthUser(1L, "소셜유저");
    User admin = buildAdmin();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(userRepository.findByPublicId(AdminAccount.PUBLIC_ID)).willReturn(Optional.of(admin));

    userService.withdraw(1L, req(CODE, PHRASE, false, null));

    then(withdrawalReasonRepository).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("withdraw_reasonText_공백만_저장안됨")
  void withdraw_blankReasonText_skipsSave() {
    User user = buildOAuthUser(1L, "소셜유저");
    User admin = buildAdmin();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(userRepository.findByPublicId(AdminAccount.PUBLIC_ID)).willReturn(Optional.of(admin));

    userService.withdraw(1L, req(CODE, PHRASE, false, "   "));

    then(withdrawalReasonRepository).shouldHaveNoInteractions();
  }

  // ============ calcActiveDays (private, getMe를 통해 간접 테스트) ============

  @Test
  @DisplayName("calcActiveDays_createdAt_null이면_0반환")
  void calcActiveDays_nullCreatedAt_returnsZero() {
    User user = buildLocalUser(1L, "유저");
    // createdAt은 BaseTimeEntity @CreatedDate로만 세팅 — null 상태 유지
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    UserResponse result = userService.getMe(1L);

    assertThat(result.activeDays()).isZero();
  }

  @Test
  @DisplayName("calcActiveDays_미래createdAt이면_0반환")
  void calcActiveDays_futureCreatedAt_returnsZero() {
    User user = buildLocalUser(1L, "유저");
    ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.now().plusDays(1));
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    UserResponse result = userService.getMe(1L);

    assertThat(result.activeDays()).isZero();
  }

  @Test
  @DisplayName("calcActiveDays_7일전createdAt이면_7반환")
  void calcActiveDays_sevenDaysAgo_returnsSeven() {
    User user = buildLocalUser(1L, "유저");
    ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.now().minusDays(7));
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    UserResponse result = userService.getMe(1L);

    assertThat(result.activeDays()).isEqualTo(7L);
  }

  // ============ agreeToCurrentTerms ============

  @Test
  @DisplayName("agreeToCurrentTerms_NULL상태에서호출_현재버전과시각기록_마케팅false")
  void agreeToCurrentTerms_recordsCurrentVersion() {
    User user = buildLocalUser(1L, "유저");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    userService.agreeToCurrentTerms(1L, new TermsAgreementRequest(false));

    assertThat(user.getTermsVersion()).isEqualTo("1.0");
    assertThat(user.getPrivacyVersion()).isEqualTo("1.0");
    assertThat(user.isMarketingAgreed()).isFalse();
    assertThat(user.getTermsAgreedAt()).isNotNull();
  }

  @Test
  @DisplayName("agreeToCurrentTerms_request_null이어도정상동의기록")
  void agreeToCurrentTerms_nullRequest_recordsAsNonMarketing() {
    User user = buildLocalUser(1L, "유저");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    userService.agreeToCurrentTerms(1L, null);

    assertThat(user.getTermsVersion()).isEqualTo("1.0");
    assertThat(user.isMarketingAgreed()).isFalse();
    assertThat(user.getTermsAgreedAt()).isNotNull();
  }

  @Test
  @DisplayName("agreeToCurrentTerms_마케팅true_기록됨")
  void agreeToCurrentTerms_marketingTrue_recordsTrue() {
    User user = buildLocalUser(1L, "유저");
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    userService.agreeToCurrentTerms(1L, new TermsAgreementRequest(true));

    assertThat(user.isMarketingAgreed()).isTrue();
  }

  @Test
  @DisplayName("agreeToCurrentTerms_사용자미존재_USER_NOT_FOUND예외")
  void agreeToCurrentTerms_userNotFound_throws() {
    given(userRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.agreeToCurrentTerms(99L, new TermsAgreementRequest(false)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }
}
