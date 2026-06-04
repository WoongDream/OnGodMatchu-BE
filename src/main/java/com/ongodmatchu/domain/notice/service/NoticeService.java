package com.ongodmatchu.domain.notice.service;

import com.ongodmatchu.domain.notice.dto.NoticeDetailResponse;
import com.ongodmatchu.domain.notice.dto.NoticeListItemResponse;
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

/** 공개 공지 조회 — 게시된 공지만 노출. 고정 우선 + 최신 게시순. 상세 조회 시 조회수 증가. */
@Service
@RequiredArgsConstructor
public class NoticeService {

  private static final int MAX_PAGE_SIZE = 50;

  private final NoticeRepository noticeRepository;

  @Transactional(readOnly = true)
  public Page<NoticeListItemResponse> getAnnouncements(Pageable pageable) {
    Pageable capped =
        PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
    return noticeRepository
        .findByStatusOrderByPinnedDescPublishedAtDesc(NoticeStatus.PUBLISHED, capped)
        .map(NoticeListItemResponse::from);
  }

  @Transactional
  public NoticeDetailResponse getAnnouncementDetail(Long id) {
    Notice notice =
        noticeRepository
            .findById(id)
            .filter(Notice::isPublished)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
    notice.increaseViewCount();
    return NoticeDetailResponse.from(notice);
  }
}
