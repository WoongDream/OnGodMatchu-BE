package com.ongodmatchu.domain.inquiry.service;

import com.ongodmatchu.domain.inquiry.dto.InquiryCreateRequest;
import com.ongodmatchu.domain.inquiry.dto.InquiryResponse;
import com.ongodmatchu.domain.inquiry.entity.Inquiry;
import com.ongodmatchu.domain.inquiry.repository.InquiryRepository;
import com.ongodmatchu.domain.notification.dto.NotificationResponse;
import com.ongodmatchu.domain.notification.repository.UserNotificationRepository;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 문의 접수 + 본인 문의 목록(받은 답변 포함). 답변은 user_notifications 연결로 조회. */
@Service
@RequiredArgsConstructor
public class InquiryService {

  private static final int MAX_PAGE_SIZE = 50;
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final InquiryRepository inquiryRepository;
  private final UserNotificationRepository userNotificationRepository;
  private final UserRepository userRepository;

  /** 문의 접수. 정지 사용자도 허용(SuspensionInterceptor 화이트리스트). */
  @Transactional
  public InquiryResponse create(Long userId, InquiryCreateRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    Inquiry inquiry =
        inquiryRepository.save(
            Inquiry.builder()
                .user(user)
                .title(request.title().trim())
                .content(request.content().trim())
                .build());
    return InquiryResponse.from(inquiry, List.of());
  }

  /** 본인 문의 목록 (최신순) + 각 문의의 받은 답변(연결 알림). size 최대 50. */
  @Transactional(readOnly = true)
  public Page<InquiryResponse> getMyInquiries(Long userId, Pageable pageable) {
    int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
    if (size <= 0) {
      size = DEFAULT_PAGE_SIZE;
    }
    Pageable capped = PageRequest.of(pageable.getPageNumber(), size);
    Page<Inquiry> page = inquiryRepository.findByUserIdOrderByCreatedAtDesc(userId, capped);

    List<Long> inquiryIds = page.getContent().stream().map(Inquiry::getId).toList();
    Map<Long, List<NotificationResponse>> answersByInquiry =
        inquiryIds.isEmpty()
            ? Map.of()
            : userNotificationRepository
                .findByRelatedInquiryIdInOrderByCreatedAtAsc(inquiryIds)
                .stream()
                .collect(
                    Collectors.groupingBy(
                        n -> n.getRelatedInquiry().getId(),
                        Collectors.mapping(NotificationResponse::from, Collectors.toList())));

    return page.map(
        inquiry ->
            InquiryResponse.from(
                inquiry, answersByInquiry.getOrDefault(inquiry.getId(), List.of())));
  }
}
