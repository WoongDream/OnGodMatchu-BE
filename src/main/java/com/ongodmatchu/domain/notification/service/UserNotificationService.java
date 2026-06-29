package com.ongodmatchu.domain.notification.service;

import com.ongodmatchu.domain.notification.dto.NotificationResponse;
import com.ongodmatchu.domain.notification.entity.UserNotification;
import com.ongodmatchu.domain.notification.repository.UserNotificationRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserNotificationService {

  private static final int MAX_PAGE_SIZE = 50;
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final UserNotificationRepository userNotificationRepository;

  /** 로그인/폴링 시 띄울 미확인 알림 (오래된 순). */
  @Transactional(readOnly = true)
  public List<NotificationResponse> getPending(Long userId) {
    return userNotificationRepository
        .findByTargetUserIdAndReadAtIsNullOrderByCreatedAtAsc(userId)
        .stream()
        .map(NotificationResponse::from)
        .toList();
  }

  /** 받은 알림 전체 (최신순). read 무관 히스토리, 받은 알림 화면용. size 최대 50. */
  @Transactional(readOnly = true)
  public Page<NotificationResponse> getReceived(Long userId, Pageable pageable) {
    int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
    if (size <= 0) {
      size = DEFAULT_PAGE_SIZE;
    }
    Pageable effective = PageRequest.of(pageable.getPageNumber(), size);
    return userNotificationRepository
        .findByTargetUserIdOrderByCreatedAtDesc(userId, effective)
        .map(NotificationResponse::from);
  }

  /** 알림 확인 처리. 본인 알림이 아니면 NOTIFICATION_NOT_FOUND 로 마스킹. */
  @Transactional
  public void markRead(Long userId, Long notificationId) {
    UserNotification notification =
        userNotificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
    if (!notification.getTargetUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
    }
    notification.markRead();
  }
}
