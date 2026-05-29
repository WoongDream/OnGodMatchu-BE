package com.ongodmatchu.domain.notice.service;

import com.ongodmatchu.domain.notice.document.NoticeDocument;
import com.ongodmatchu.domain.notice.dto.NoticeDetailResponse;
import com.ongodmatchu.domain.notice.dto.NoticeListItemResponse;
import com.ongodmatchu.domain.notice.loader.NoticeMarkdownLoader;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NoticeService {

  private static final int MAX_PAGE_SIZE = 50;
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final NoticeMarkdownLoader loader;

  public Page<NoticeListItemResponse> getAnnouncements(Pageable pageable) {
    int size = effectiveSize(pageable.getPageSize());
    int page = Math.max(pageable.getPageNumber(), 0);

    List<NoticeDocument> all = loader.findAllAnnouncements();
    int total = all.size();
    int from = Math.min(page * size, total);
    int to = Math.min(from + size, total);

    List<NoticeListItemResponse> content =
        all.subList(from, to).stream().map(NoticeListItemResponse::from).toList();

    return new PageImpl<>(content, PageRequest.of(page, size), total);
  }

  public NoticeDetailResponse getAnnouncementDetail(String slug) {
    return loader
        .findAnnouncementBySlug(slug)
        .map(NoticeDetailResponse::from)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
  }

  private static int effectiveSize(int requested) {
    if (requested <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
