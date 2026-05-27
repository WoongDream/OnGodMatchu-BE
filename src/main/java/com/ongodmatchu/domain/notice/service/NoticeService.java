package com.ongodmatchu.domain.notice.service;

import com.ongodmatchu.domain.notice.dto.NoticeDetailResponse;
import com.ongodmatchu.domain.notice.dto.NoticeListItemResponse;
import com.ongodmatchu.domain.notice.entity.Notice;
import com.ongodmatchu.domain.notice.entity.NoticeType;
import com.ongodmatchu.domain.notice.repository.NoticeRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoticeService {

  private static final int MAX_PAGE_SIZE = 50;
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final NoticeRepository noticeRepository;

  @Transactional(readOnly = true)
  public Page<NoticeListItemResponse> getList(NoticeType type, Pageable pageable) {
    Pageable effective = applyPageDefaults(pageable);
    return noticeRepository
        .findByTypeAndPublishedAtIsNotNull(type, effective)
        .map(NoticeListItemResponse::from);
  }

  @Transactional(readOnly = true)
  public NoticeDetailResponse getDetail(NoticeType type, Long id) {
    Notice notice =
        noticeRepository
            .findByIdAndTypeAndPublishedAtIsNotNull(id, type)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
    return NoticeDetailResponse.from(notice);
  }

  private Pageable applyPageDefaults(Pageable pageable) {
    int size = pageable.getPageSize();
    if (size <= 0) {
      size = DEFAULT_PAGE_SIZE;
    } else if (size > MAX_PAGE_SIZE) {
      size = MAX_PAGE_SIZE;
    }
    Sort sort = Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by(Sort.Direction.DESC, "id"));
    return PageRequest.of(pageable.getPageNumber(), size, sort);
  }
}
