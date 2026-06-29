package com.ongodmatchu.domain.notice.service;

import com.ongodmatchu.domain.notice.dto.AdminNoticeListItemResponse;
import com.ongodmatchu.domain.notice.dto.AdminNoticeResponse;
import com.ongodmatchu.domain.notice.dto.NoticeCreateRequest;
import com.ongodmatchu.domain.notice.dto.NoticeStatsResponse;
import com.ongodmatchu.domain.notice.dto.NoticeUpdateRequest;
import com.ongodmatchu.domain.notice.entity.Notice;
import com.ongodmatchu.domain.notice.entity.NoticeStatus;
import com.ongodmatchu.domain.notice.repository.NoticeRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 백오피스 공지 관리 — OWNER 전용(SecurityConfig 가드). 목록/통계/단건/생성/수정/삭제. */
@Service
@RequiredArgsConstructor
public class AdminNoticeService {

  private static final int MAX_PAGE_SIZE = 50;

  private final NoticeRepository noticeRepository;

  @Transactional(readOnly = true)
  public Page<AdminNoticeListItemResponse> getNotices(
      NoticeFilter filter, String query, Pageable pageable) {
    String q = (query == null || query.isBlank()) ? null : query.trim();
    Pageable capped =
        PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
    return noticeRepository
        .searchForAdmin(filter.status(), filter.pinned(), q, capped)
        .map(AdminNoticeListItemResponse::from);
  }

  @Transactional(readOnly = true)
  public NoticeStatsResponse getStats() {
    long total = noticeRepository.count();
    // 게시 = 게시 전체(고정 포함), 고정 = 게시 중 pinned (게시의 부분집합)
    long published = noticeRepository.countByStatus(NoticeStatus.PUBLISHED);
    long pinned = noticeRepository.countByStatusAndPinned(NoticeStatus.PUBLISHED, true);
    long draft = noticeRepository.countByStatus(NoticeStatus.DRAFT);
    return new NoticeStatsResponse(total, published, pinned, draft);
  }

  @Transactional(readOnly = true)
  public AdminNoticeResponse getNotice(Long id) {
    return AdminNoticeResponse.from(get(id));
  }

  @Transactional
  public AdminNoticeResponse create(NoticeCreateRequest request) {
    Notice notice =
        noticeRepository.save(
            Notice.builder()
                .title(request.title())
                .content(request.content())
                .status(request.status())
                .pinned(request.pinned())
                .build());
    return AdminNoticeResponse.from(notice);
  }

  @Transactional
  public AdminNoticeResponse update(Long id, NoticeUpdateRequest request) {
    Notice notice = get(id);
    notice.updateContent(request.title(), request.content());
    if (request.status() != null) {
      notice.changeStatus(request.status());
    }
    if (request.pinned() != null) {
      notice.changePinned(request.pinned());
    }
    return AdminNoticeResponse.from(notice);
  }

  @Transactional
  public void delete(Long id) {
    noticeRepository.delete(get(id));
  }

  private Notice get(Long id) {
    return noticeRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
  }
}
