package com.ongodmatchu.domain.notification.repository;

import com.ongodmatchu.domain.notification.entity.UserNotification;
import java.util.Collection;
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

  /** 한 문의에 달린 답변 알림 (오래된 순) — BO 상세용. */
  List<UserNotification> findByRelatedInquiryIdOrderByCreatedAtAsc(Long inquiryId);

  /** 여러 문의의 답변 알림 일괄 조회 (오래된 순) — 본인 문의 목록 N+1 회피. */
  List<UserNotification> findByRelatedInquiryIdInOrderByCreatedAtAsc(Collection<Long> inquiryIds);
}
