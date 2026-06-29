package com.ongodmatchu.domain.inquiry.service;

import com.ongodmatchu.domain.admin.dto.SendNotificationRequest;
import com.ongodmatchu.domain.inquiry.dto.AdminInquiryDetailResponse;
import com.ongodmatchu.domain.inquiry.dto.AdminInquiryListItemResponse;
import com.ongodmatchu.domain.inquiry.dto.InquiryStatsResponse;
import com.ongodmatchu.domain.inquiry.entity.Inquiry;
import com.ongodmatchu.domain.inquiry.entity.InquiryStatus;
import com.ongodmatchu.domain.inquiry.repository.InquiryRepository;
import com.ongodmatchu.domain.notification.dto.NotificationResponse;
import com.ongodmatchu.domain.notification.entity.UserNotification;
import com.ongodmatchu.domain.notification.repository.UserNotificationRepository;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.entity.UserStatus;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.s3.S3Service;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 백오피스 문의 관리 — 목록/통계/상세/상태변경/답변. ADMIN+OWNER (SecurityConfig 가드). */
@Service
@RequiredArgsConstructor
public class AdminInquiryService {

  private static final int MAX_PAGE_SIZE = 50;

  private final InquiryRepository inquiryRepository;
  private final UserNotificationRepository userNotificationRepository;
  private final UserRepository userRepository;
  private final S3Service s3Service;

  @Value("${app.profile.default-image-url}")
  private String defaultProfileImageUrl;

  @Transactional(readOnly = true)
  public Page<AdminInquiryListItemResponse> getInquiries(
      InquiryFilter filter, String query, Pageable pageable) {
    String q = (query == null || query.isBlank()) ? null : query.trim();
    Pageable capped =
        PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
    return inquiryRepository
        .searchForAdmin(filter.status(), q, capped)
        .map(
            inquiry ->
                AdminInquiryListItemResponse.from(inquiry, resolveImageUrl(inquiry.getUser())));
  }

  @Transactional(readOnly = true)
  public InquiryStatsResponse getStats() {
    return new InquiryStatsResponse(
        inquiryRepository.count(),
        inquiryRepository.countByStatus(InquiryStatus.PENDING),
        inquiryRepository.countByStatus(InquiryStatus.IN_PROGRESS),
        inquiryRepository.countByStatus(InquiryStatus.DONE));
  }

  @Transactional(readOnly = true)
  public AdminInquiryDetailResponse getInquiry(Long id) {
    Inquiry inquiry = get(id);
    return toDetail(inquiry);
  }

  /** 처리 상태 변경 (수동, 답변 발송과 독립). */
  @Transactional
  public AdminInquiryDetailResponse changeStatus(Long id, InquiryStatus status) {
    Inquiry inquiry = get(id);
    inquiry.changeStatus(status);
    return toDetail(inquiry);
  }

  /** 답변 발송 — 문의 작성자에게 알림을 보내고 문의에 연결(여러 번 가능). 상태는 자동 전환하지 않음. */
  @Transactional
  public NotificationResponse answer(
      Long actorId, Long inquiryId, SendNotificationRequest request) {
    User actor =
        userRepository
            .findById(actorId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    Inquiry inquiry = get(inquiryId);
    User target = inquiry.getUser();
    if (target.getStatus() == UserStatus.WITHDRAWN) {
      throw new BusinessException(ErrorCode.ADMIN_TARGET_INVALID);
    }
    UserNotification notification =
        userNotificationRepository.save(
            UserNotification.builder()
                .targetUser(target)
                .sender(actor)
                .type(request.type())
                .title(request.title().trim())
                .content(request.content().trim())
                .relatedInquiry(inquiry)
                .build());
    return NotificationResponse.from(notification);
  }

  // --- helpers ---

  private AdminInquiryDetailResponse toDetail(Inquiry inquiry) {
    List<NotificationResponse> answers =
        userNotificationRepository
            .findByRelatedInquiryIdOrderByCreatedAtAsc(inquiry.getId())
            .stream()
            .map(NotificationResponse::from)
            .toList();
    return AdminInquiryDetailResponse.of(inquiry, resolveImageUrl(inquiry.getUser()), answers);
  }

  private Inquiry get(Long id) {
    return inquiryRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
  }

  private String resolveImageUrl(User user) {
    String key = user.getProfileImageKey();
    return key == null ? defaultProfileImageUrl : s3Service.generateViewUrl(key).viewUrl();
  }
}
