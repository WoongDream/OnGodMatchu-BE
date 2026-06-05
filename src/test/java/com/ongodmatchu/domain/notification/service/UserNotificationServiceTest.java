package com.ongodmatchu.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.notification.dto.NotificationResponse;
import com.ongodmatchu.domain.notification.entity.NotificationType;
import com.ongodmatchu.domain.notification.entity.UserNotification;
import com.ongodmatchu.domain.notification.repository.UserNotificationRepository;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
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
class UserNotificationServiceTest {

  @InjectMocks private UserNotificationService userNotificationService;
  @Mock private UserNotificationRepository userNotificationRepository;

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

  private UserNotification notification(long id, User target) {
    UserNotification n =
        UserNotification.builder()
            .targetUser(target)
            .sender(user(99L))
            .type(NotificationType.INFO)
            .title("제목")
            .content("내용")
            .build();
    ReflectionTestUtils.setField(n, "id", id);
    return n;
  }

  private ErrorCode errorCodeOf(Throwable t) {
    return ((BusinessException) t).getErrorCode();
  }

  /** PageRequest 는 size>=1 만 허용하므로, size<=0 분기 검증용 Pageable 을 직접 만든다. */
  private Pageable zeroSizePageable() {
    return new org.springframework.data.domain.AbstractPageRequest(0, 1) {
      @Override
      public int getPageSize() {
        return 0;
      }

      @Override
      public org.springframework.data.domain.Sort getSort() {
        return org.springframework.data.domain.Sort.unsorted();
      }

      @Override
      public Pageable next() {
        return this;
      }

      @Override
      public Pageable previous() {
        return this;
      }

      @Override
      public Pageable first() {
        return this;
      }

      @Override
      public Pageable withPage(int pageNumber) {
        return this;
      }
    };
  }

  // ============ getPending ============

  @Test
  @DisplayName("getPending — 미확인 알림을 NotificationResponse 로 매핑")
  void getPending_mapsToResponse() {
    User me = user(1L);
    given(userNotificationRepository.findByTargetUserIdAndReadAtIsNullOrderByCreatedAtAsc(1L))
        .willReturn(List.of(notification(10L, me)));

    List<NotificationResponse> result = userNotificationService.getPending(1L);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(10L);
    assertThat(result.get(0).type()).isEqualTo("INFO");
    assertThat(result.get(0).typeLabel()).isEqualTo("안내");
    assertThat(result.get(0).title()).isEqualTo("제목");
    assertThat(result.get(0).senderLabel()).isEqualTo("운영팀");
  }

  // ============ getReceived ============

  @Test
  @DisplayName("getReceived — 본인 받은 알림 페이지를 NotificationResponse 로 매핑")
  void getReceived_mapsToResponsePage() {
    User me = user(1L);
    Pageable pageable = PageRequest.of(0, 20);
    Page<UserNotification> page = new PageImpl<>(List.of(notification(10L, me)), pageable, 1);
    given(userNotificationRepository.findByTargetUserIdOrderByCreatedAtDesc(eq(1L), any()))
        .willReturn(page);

    Page<NotificationResponse> result = userNotificationService.getReceived(1L, pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).id()).isEqualTo(10L);
    assertThat(result.getContent().get(0).type()).isEqualTo("INFO");
    assertThat(result.getContent().get(0).typeLabel()).isEqualTo("안내");
    assertThat(result.getContent().get(0).title()).isEqualTo("제목");
    assertThat(result.getContent().get(0).senderLabel()).isEqualTo("운영팀");
    then(userNotificationRepository).should().findByTargetUserIdOrderByCreatedAtDesc(eq(1L), any());
  }

  @Test
  @DisplayName("getReceived — size 100 요청이면 effective Pageable size 가 50 으로 cap")
  void getReceived_capsSizeTo50() {
    User me = user(1L);
    given(userNotificationRepository.findByTargetUserIdOrderByCreatedAtDesc(eq(1L), any()))
        .willReturn(new PageImpl<>(List.of(notification(10L, me))));

    userNotificationService.getReceived(1L, PageRequest.of(2, 100));

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(userNotificationRepository)
        .should()
        .findByTargetUserIdOrderByCreatedAtDesc(eq(1L), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
  }

  @Test
  @DisplayName("getReceived — size<=0 이면 기본 20 적용")
  void getReceived_defaultsSizeWhenNonPositive() {
    User me = user(1L);
    given(userNotificationRepository.findByTargetUserIdOrderByCreatedAtDesc(eq(1L), any()))
        .willReturn(new PageImpl<>(List.of(notification(10L, me))));

    userNotificationService.getReceived(1L, zeroSizePageable());

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(userNotificationRepository)
        .should()
        .findByTargetUserIdOrderByCreatedAtDesc(eq(1L), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
  }

  // ============ markRead ============

  @Test
  @DisplayName("markRead — 본인 알림이면 readAt 세팅")
  void markRead_ownNotification_marksRead() {
    User me = user(1L);
    UserNotification n = notification(10L, me);
    given(userNotificationRepository.findById(10L)).willReturn(Optional.of(n));

    userNotificationService.markRead(1L, 10L);

    assertThat(n.isRead()).isTrue();
  }

  @Test
  @DisplayName("markRead — 타인 알림이면 NOTIFICATION_NOT_FOUND, readAt 미변경")
  void markRead_othersNotification_notFound() {
    User other = user(2L);
    UserNotification n = notification(10L, other);
    given(userNotificationRepository.findById(10L)).willReturn(Optional.of(n));

    assertThatThrownBy(() -> userNotificationService.markRead(1L, 10L))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    assertThat(n.isRead()).isFalse();
  }

  @Test
  @DisplayName("markRead — 존재하지 않는 알림이면 NOTIFICATION_NOT_FOUND")
  void markRead_missing_notFound() {
    given(userNotificationRepository.findById(any())).willReturn(Optional.empty());

    assertThatThrownBy(() -> userNotificationService.markRead(1L, 999L))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    then(userNotificationRepository).should(never()).save(any());
  }
}
