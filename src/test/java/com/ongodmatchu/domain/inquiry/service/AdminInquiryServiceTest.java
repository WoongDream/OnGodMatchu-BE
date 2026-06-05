package com.ongodmatchu.domain.inquiry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.admin.dto.SendNotificationRequest;
import com.ongodmatchu.domain.inquiry.dto.AdminInquiryDetailResponse;
import com.ongodmatchu.domain.inquiry.dto.AdminInquiryListItemResponse;
import com.ongodmatchu.domain.inquiry.dto.InquiryStatsResponse;
import com.ongodmatchu.domain.inquiry.entity.Inquiry;
import com.ongodmatchu.domain.inquiry.entity.InquiryStatus;
import com.ongodmatchu.domain.inquiry.repository.InquiryRepository;
import com.ongodmatchu.domain.notification.dto.NotificationResponse;
import com.ongodmatchu.domain.notification.entity.NotificationType;
import com.ongodmatchu.domain.notification.entity.UserNotification;
import com.ongodmatchu.domain.notification.repository.UserNotificationRepository;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.s3.S3Service;
import com.ongodmatchu.infra.s3.ViewUrlResponse;
import java.time.Instant;
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
class AdminInquiryServiceTest {

  private static final String DEFAULT_IMAGE = "https://cdn.example.com/default.png";

  @InjectMocks private AdminInquiryService adminInquiryService;
  @Mock private InquiryRepository inquiryRepository;
  @Mock private UserNotificationRepository userNotificationRepository;
  @Mock private UserRepository userRepository;
  @Mock private S3Service s3Service;

  private User user(long id) {
    return user(id, true);
  }

  private User user(long id, boolean active) {
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
    ReflectionTestUtils.setField(u, "isActive", active);
    return u;
  }

  private Inquiry inquiry(long id, User user, String title) {
    Inquiry i = Inquiry.builder().user(user).title(title).content("내용 " + id).build();
    ReflectionTestUtils.setField(i, "id", id);
    ReflectionTestUtils.setField(i, "createdAt", LocalDateTime.now());
    return i;
  }

  private UserNotification answer(long id, User target, Inquiry relatedInquiry) {
    UserNotification n =
        UserNotification.builder()
            .targetUser(target)
            .sender(user(99L))
            .type(NotificationType.INFO)
            .title("답변 제목")
            .content("답변 내용")
            .relatedInquiry(relatedInquiry)
            .build();
    ReflectionTestUtils.setField(n, "id", id);
    ReflectionTestUtils.setField(n, "createdAt", LocalDateTime.now());
    return n;
  }

  private SendNotificationRequest sendRequest() {
    return new SendNotificationRequest(NotificationType.INFO, "  답변제목  ", "  답변내용  ");
  }

  private ErrorCode errorCodeOf(Throwable t) {
    return ((BusinessException) t).getErrorCode();
  }

  private void setDefaultImage() {
    ReflectionTestUtils.setField(adminInquiryService, "defaultProfileImageUrl", DEFAULT_IMAGE);
  }

  // ============ getInquiries ============

  @Test
  @DisplayName("getInquiries — PENDING 필터는 status=PENDING 위임, query trim, key 없으면 기본 이미지")
  void getInquiries_pendingFilter_delegatesAndDefaultImage() {
    setDefaultImage();
    User author = user(2L);
    Inquiry i = inquiry(10L, author, "문의제목");
    given(inquiryRepository.searchForAdmin(eq(InquiryStatus.PENDING), eq("hong"), any()))
        .willReturn(new PageImpl<>(List.of(i)));

    Page<AdminInquiryListItemResponse> result =
        adminInquiryService.getInquiries(InquiryFilter.PENDING, "  hong  ", PageRequest.of(0, 20));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).id()).isEqualTo(10L);
    assertThat(result.getContent().get(0).status()).isEqualTo("PENDING");
    assertThat(result.getContent().get(0).author().profileImageUrl()).isEqualTo(DEFAULT_IMAGE);
    then(s3Service).should(never()).generateViewUrl(any());
  }

  @Test
  @DisplayName("getInquiries — ALL 필터는 status=null 위임, 빈 query 는 null 로 정규화")
  void getInquiries_allFilter_blankQueryNormalized() {
    given(inquiryRepository.searchForAdmin(isNull(), isNull(), any()))
        .willReturn(new PageImpl<>(List.of()));

    adminInquiryService.getInquiries(InquiryFilter.ALL, "   ", PageRequest.of(0, 20));

    then(inquiryRepository).should().searchForAdmin(isNull(), isNull(), any());
  }

  @Test
  @DisplayName("getInquiries — pageSize 50 초과는 50 으로 cap")
  void getInquiries_pageSizeCapped() {
    given(inquiryRepository.searchForAdmin(any(), any(), any()))
        .willReturn(new PageImpl<>(List.of()));

    adminInquiryService.getInquiries(InquiryFilter.DONE, "x", PageRequest.of(1, 200));

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(inquiryRepository)
        .should()
        .searchForAdmin(eq(InquiryStatus.DONE), eq("x"), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
  }

  @Test
  @DisplayName("getInquiries — profileImageKey 있으면 s3Service.generateViewUrl 의 viewUrl 사용")
  void getInquiries_withImageKey_usesPresignedUrl() {
    User author = user(2L);
    ReflectionTestUtils.setField(author, "profileImageKey", "profile/key-1");
    Inquiry i = inquiry(10L, author, "문의제목");
    given(inquiryRepository.searchForAdmin(isNull(), isNull(), any()))
        .willReturn(new PageImpl<>(List.of(i)));
    given(s3Service.generateViewUrl("profile/key-1"))
        .willReturn(
            new ViewUrlResponse(
                "https://signed.example.com/v", "profile/key-1", 60L, Instant.now()));

    Page<AdminInquiryListItemResponse> result =
        adminInquiryService.getInquiries(InquiryFilter.ALL, null, PageRequest.of(0, 20));

    assertThat(result.getContent().get(0).author().profileImageUrl())
        .isEqualTo("https://signed.example.com/v");
  }

  // ============ getStats ============

  @Test
  @DisplayName("getStats — total/pending/inProgress/done 카운트를 매핑")
  void getStats_mapsCounts() {
    given(inquiryRepository.count()).willReturn(30L);
    given(inquiryRepository.countByStatus(InquiryStatus.PENDING)).willReturn(10L);
    given(inquiryRepository.countByStatus(InquiryStatus.IN_PROGRESS)).willReturn(8L);
    given(inquiryRepository.countByStatus(InquiryStatus.DONE)).willReturn(12L);

    InquiryStatsResponse result = adminInquiryService.getStats();

    assertThat(result.total()).isEqualTo(30L);
    assertThat(result.pending()).isEqualTo(10L);
    assertThat(result.inProgress()).isEqualTo(8L);
    assertThat(result.done()).isEqualTo(12L);
  }

  // ============ getInquiry ============

  @Test
  @DisplayName("getInquiry — 상세 + 연결 답변 매핑")
  void getInquiry_found_withAnswers() {
    setDefaultImage();
    User author = user(2L);
    Inquiry i = inquiry(10L, author, "문의제목");
    given(inquiryRepository.findById(10L)).willReturn(Optional.of(i));
    given(userNotificationRepository.findByRelatedInquiryIdOrderByCreatedAtAsc(10L))
        .willReturn(List.of(answer(100L, author, i)));

    AdminInquiryDetailResponse result = adminInquiryService.getInquiry(10L);

    assertThat(result.id()).isEqualTo(10L);
    assertThat(result.title()).isEqualTo("문의제목");
    assertThat(result.status()).isEqualTo("PENDING");
    assertThat(result.answers()).hasSize(1);
    assertThat(result.answers().get(0).id()).isEqualTo(100L);
  }

  @Test
  @DisplayName("getInquiry — 미존재 시 INQUIRY_NOT_FOUND")
  void getInquiry_notFound() {
    given(inquiryRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> adminInquiryService.getInquiry(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
  }

  // ============ changeStatus ============

  @Test
  @DisplayName("changeStatus — 상태 변경 후 상세 반환")
  void changeStatus_success() {
    setDefaultImage();
    User author = user(2L);
    Inquiry i = inquiry(10L, author, "문의제목");
    given(inquiryRepository.findById(10L)).willReturn(Optional.of(i));
    given(userNotificationRepository.findByRelatedInquiryIdOrderByCreatedAtAsc(10L))
        .willReturn(List.of());

    AdminInquiryDetailResponse result =
        adminInquiryService.changeStatus(10L, InquiryStatus.IN_PROGRESS);

    assertThat(i.getStatus()).isEqualTo(InquiryStatus.IN_PROGRESS);
    assertThat(result.status()).isEqualTo("IN_PROGRESS");
    assertThat(result.statusLabel()).isEqualTo("처리중");
    assertThat(result.answers()).isEmpty();
  }

  @Test
  @DisplayName("changeStatus — 미존재 시 INQUIRY_NOT_FOUND")
  void changeStatus_notFound() {
    given(inquiryRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> adminInquiryService.changeStatus(99L, InquiryStatus.DONE))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
  }

  // ============ answer ============

  @Test
  @DisplayName("answer — 작성자에게 알림 저장(relatedInquiry 연결, trim), 상태는 미변경")
  void answer_savesNotificationAndKeepsStatus() {
    User actor = user(1L);
    User author = user(2L);
    Inquiry i = inquiry(10L, author, "문의제목");
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(inquiryRepository.findById(10L)).willReturn(Optional.of(i));
    given(userNotificationRepository.save(any(UserNotification.class)))
        .willAnswer(inv -> inv.getArgument(0));

    NotificationResponse result = adminInquiryService.answer(1L, 10L, sendRequest());

    ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
    then(userNotificationRepository).should().save(captor.capture());
    UserNotification saved = captor.getValue();
    assertThat(saved.getTargetUser()).isEqualTo(author);
    assertThat(saved.getSender()).isEqualTo(actor);
    assertThat(saved.getRelatedInquiry()).isEqualTo(i);
    assertThat(saved.getTitle()).isEqualTo("답변제목");
    assertThat(saved.getContent()).isEqualTo("답변내용");
    // 상태는 자동 전환하지 않음
    assertThat(i.getStatus()).isEqualTo(InquiryStatus.PENDING);
    assertThat(result.title()).isEqualTo("답변제목");
  }

  @Test
  @DisplayName("answer — 작성자가 WITHDRAWN 이면 ADMIN_TARGET_INVALID, save 미호출")
  void answer_withdrawnAuthor_targetInvalid() {
    User actor = user(1L);
    User author = user(2L, false);
    Inquiry i = inquiry(10L, author, "문의제목");
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(inquiryRepository.findById(10L)).willReturn(Optional.of(i));

    assertThatThrownBy(() -> adminInquiryService.answer(1L, 10L, sendRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.ADMIN_TARGET_INVALID);
    then(userNotificationRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("answer — actor 미존재 시 USER_NOT_FOUND, 문의 조회 전 단락")
  void answer_actorNotFound() {
    given(userRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> adminInquiryService.answer(1L, 10L, sendRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
    then(inquiryRepository).should(never()).findById(any());
    then(userNotificationRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("answer — 문의 미존재 시 INQUIRY_NOT_FOUND")
  void answer_inquiryNotFound() {
    User actor = user(1L);
    given(userRepository.findById(1L)).willReturn(Optional.of(actor));
    given(inquiryRepository.findById(10L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> adminInquiryService.answer(1L, 10L, sendRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
    then(userNotificationRepository).should(never()).save(any());
  }
}
