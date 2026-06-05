package com.ongodmatchu.domain.inquiry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.inquiry.dto.InquiryCreateRequest;
import com.ongodmatchu.domain.inquiry.dto.InquiryResponse;
import com.ongodmatchu.domain.inquiry.entity.Inquiry;
import com.ongodmatchu.domain.inquiry.entity.InquiryStatus;
import com.ongodmatchu.domain.inquiry.repository.InquiryRepository;
import com.ongodmatchu.domain.notification.entity.NotificationType;
import com.ongodmatchu.domain.notification.entity.UserNotification;
import com.ongodmatchu.domain.notification.repository.UserNotificationRepository;
import com.ongodmatchu.domain.user.entity.AuthProvider;
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
class InquiryServiceTest {

  @InjectMocks private InquiryService inquiryService;
  @Mock private InquiryRepository inquiryRepository;
  @Mock private UserNotificationRepository userNotificationRepository;
  @Mock private UserRepository userRepository;

  private User user(long id) {
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
    return u;
  }

  private Inquiry inquiry(long id, User user, String title, String content) {
    Inquiry i = Inquiry.builder().user(user).title(title).content(content).build();
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

  private ErrorCode errorCodeOf(Throwable t) {
    return ((BusinessException) t).getErrorCode();
  }

  // ============ create ============

  @Test
  @DisplayName("create — 정상 접수: title/content trim, status PENDING, answers 빈 배열")
  void create_success_trimsAndPending() {
    User me = user(1L);
    given(userRepository.findById(1L)).willReturn(Optional.of(me));
    given(inquiryRepository.save(any(Inquiry.class))).willAnswer(inv -> inv.getArgument(0));

    InquiryResponse result =
        inquiryService.create(1L, new InquiryCreateRequest("  제목  ", "  내용  "));

    ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
    then(inquiryRepository).should().save(captor.capture());
    assertThat(captor.getValue().getTitle()).isEqualTo("제목");
    assertThat(captor.getValue().getContent()).isEqualTo("내용");
    assertThat(captor.getValue().getStatus()).isEqualTo(InquiryStatus.PENDING);
    assertThat(captor.getValue().getUser()).isEqualTo(me);

    assertThat(result.title()).isEqualTo("제목");
    assertThat(result.content()).isEqualTo("내용");
    assertThat(result.status()).isEqualTo("PENDING");
    assertThat(result.statusLabel()).isEqualTo("대기");
    assertThat(result.answers()).isEmpty();
  }

  @Test
  @DisplayName("create — userId 미존재 시 USER_NOT_FOUND, save 미호출")
  void create_userNotFound() {
    given(userRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> inquiryService.create(1L, new InquiryCreateRequest("제목", "내용")))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
    then(inquiryRepository).should(never()).save(any());
  }

  // ============ getMyInquiries ============

  @Test
  @DisplayName("getMyInquiries — 최신순 페이지 매핑 + 연결 답변 그룹핑되어 answers 채워짐")
  void getMyInquiries_mapsAndGroupsAnswers() {
    User me = user(1L);
    Inquiry i1 = inquiry(10L, me, "문의1", "내용1");
    Inquiry i2 = inquiry(11L, me, "문의2", "내용2");
    Pageable pageable = PageRequest.of(0, 20);
    given(inquiryRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any()))
        .willReturn(new PageImpl<>(List.of(i1, i2), pageable, 2));
    given(userNotificationRepository.findByRelatedInquiryIdInOrderByCreatedAtAsc(anyList()))
        .willReturn(List.of(answer(100L, me, i1), answer(101L, me, i1)));

    Page<InquiryResponse> result = inquiryService.getMyInquiries(1L, pageable);

    assertThat(result.getContent()).hasSize(2);
    InquiryResponse first = result.getContent().get(0);
    assertThat(first.id()).isEqualTo(10L);
    assertThat(first.answers()).hasSize(2);
    assertThat(first.answers().get(0).id()).isEqualTo(100L);
    // 답변 없는 문의는 빈 배열
    InquiryResponse second = result.getContent().get(1);
    assertThat(second.id()).isEqualTo(11L);
    assertThat(second.answers()).isEmpty();
  }

  @Test
  @DisplayName("getMyInquiries — 빈 페이지면 findByRelatedInquiryIdIn... 미호출")
  void getMyInquiries_emptyPage_skipsAnswerLookup() {
    Pageable pageable = PageRequest.of(0, 20);
    given(inquiryRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any()))
        .willReturn(new PageImpl<>(List.of(), pageable, 0));

    Page<InquiryResponse> result = inquiryService.getMyInquiries(1L, pageable);

    assertThat(result.getContent()).isEmpty();
    then(userNotificationRepository)
        .should(never())
        .findByRelatedInquiryIdInOrderByCreatedAtAsc(any());
  }

  @Test
  @DisplayName("getMyInquiries — size 100 요청이면 effective Pageable size 가 50 으로 cap")
  void getMyInquiries_capsSizeTo50() {
    given(inquiryRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any()))
        .willReturn(new PageImpl<>(List.of()));

    inquiryService.getMyInquiries(1L, PageRequest.of(2, 100));

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(inquiryRepository).should().findByUserIdOrderByCreatedAtDesc(eq(1L), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
  }
}
