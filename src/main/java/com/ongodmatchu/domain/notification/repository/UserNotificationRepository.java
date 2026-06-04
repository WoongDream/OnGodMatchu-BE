package com.ongodmatchu.domain.notification.repository;

import com.ongodmatchu.domain.notification.entity.UserNotification;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

  /** 미확인 알림 (오래된 순) — 로그인/폴링 시 순서대로 처리. */
  List<UserNotification> findByTargetUserIdAndReadAtIsNullOrderByCreatedAtAsc(Long targetUserId);

  /** 받은 알림 전체 (최신순) — 받음알림 메뉴(추후)용. */
  Page<UserNotification> findByTargetUserIdOrderByCreatedAtDesc(
      Long targetUserId, Pageable pageable);
}
