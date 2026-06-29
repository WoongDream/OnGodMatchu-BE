package com.ongodmatchu.domain.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ongodmatchu.domain.notice.dto.NoticeDetailResponse;
import com.ongodmatchu.domain.notice.dto.NoticeListItemResponse;
import com.ongodmatchu.domain.notice.entity.Notice;
import com.ongodmatchu.domain.notice.entity.NoticeStatus;
import com.ongodmatchu.domain.notice.repository.NoticeRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

  @InjectMocks private NoticeService noticeService;
  @Mock private NoticeRepository noticeRepository;
  @Captor private ArgumentCaptor<Pageable> pageableCaptor;

  private Notice buildNotice(Long id, String title, NoticeStatus status, boolean pinned) {
    Notice notice =
        Notice.builder().title(title).content(title + " 본문").status(status).pinned(pinned).build();
    ReflectionTestUtils.setField(notice, "id", id);
    return notice;
  }

  // ============ getAnnouncements ============

  @Test
  @DisplayName("getAnnouncements_게시공지를_ListItemResponse로_매핑한다")
  void getAnnouncements_mapsToResponse() {
    Notice notice = buildNotice(1L, "게시 공지", NoticeStatus.PUBLISHED, true);
    notice.increaseViewCount();
    given(
            noticeRepository.findByStatusOrderByPinnedDescPublishedAtDesc(
                eq(NoticeStatus.PUBLISHED), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(notice)));

    Page<NoticeListItemResponse> result = noticeService.getAnnouncements(PageRequest.of(0, 10));

    assertThat(result.getTotalElements()).isEqualTo(1L);
    NoticeListItemResponse item = result.getContent().get(0);
    assertThat(item.id()).isEqualTo(1L);
    assertThat(item.title()).isEqualTo("게시 공지");
    assertThat(item.pinned()).isTrue();
    assertThat(item.viewCount()).isEqualTo(1L);
  }

  @Test
  @DisplayName("getAnnouncements_게시공지없으면_빈페이지")
  void getAnnouncements_empty() {
    given(
            noticeRepository.findByStatusOrderByPinnedDescPublishedAtDesc(
                eq(NoticeStatus.PUBLISHED), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    Page<NoticeListItemResponse> result = noticeService.getAnnouncements(PageRequest.of(0, 10));

    assertThat(result.getContent()).isEmpty();
  }

  @Test
  @DisplayName("getAnnouncements_size가50초과면_50으로_cap한다")
  void getAnnouncements_capsPageSizeAt50() {
    given(
            noticeRepository.findByStatusOrderByPinnedDescPublishedAtDesc(
                eq(NoticeStatus.PUBLISHED), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    noticeService.getAnnouncements(PageRequest.of(2, 100));

    verify(noticeRepository)
        .findByStatusOrderByPinnedDescPublishedAtDesc(
            eq(NoticeStatus.PUBLISHED), pageableCaptor.capture());
    Pageable used = pageableCaptor.getValue();
    assertThat(used.getPageSize()).isEqualTo(50);
    assertThat(used.getPageNumber()).isEqualTo(2);
  }

  @Test
  @DisplayName("getAnnouncements_size가50이하면_요청값을_그대로_사용한다")
  void getAnnouncements_keepsPageSizeWhenWithinLimit() {
    given(
            noticeRepository.findByStatusOrderByPinnedDescPublishedAtDesc(
                eq(NoticeStatus.PUBLISHED), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    noticeService.getAnnouncements(PageRequest.of(0, 20));

    verify(noticeRepository)
        .findByStatusOrderByPinnedDescPublishedAtDesc(
            eq(NoticeStatus.PUBLISHED), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
  }

  // ============ getAnnouncementDetail ============

  @Test
  @DisplayName("getAnnouncementDetail_게시공지_조회수증가후_DetailResponse반환")
  void getAnnouncementDetail_published_increasesViewCount() {
    Notice notice = buildNotice(1L, "게시 공지", NoticeStatus.PUBLISHED, false);
    given(noticeRepository.findById(1L)).willReturn(Optional.of(notice));

    NoticeDetailResponse result = noticeService.getAnnouncementDetail(1L);

    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.title()).isEqualTo("게시 공지");
    assertThat(result.content()).isEqualTo("게시 공지 본문");
    assertThat(result.viewCount()).isEqualTo(1L);
    assertThat(notice.getViewCount()).isEqualTo(1L);
  }

  @Test
  @DisplayName("getAnnouncementDetail_DRAFT공지는_NOTICE_NOT_FOUND_조회수증가안함")
  void getAnnouncementDetail_draft_throwsNotFound() {
    Notice draft = buildNotice(1L, "임시저장", NoticeStatus.DRAFT, false);
    given(noticeRepository.findById(1L)).willReturn(Optional.of(draft));

    assertThatThrownBy(() -> noticeService.getAnnouncementDetail(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
    assertThat(draft.getViewCount()).isZero();
  }

  @Test
  @DisplayName("getAnnouncementDetail_없는id면_NOTICE_NOT_FOUND")
  void getAnnouncementDetail_notFound_throws() {
    given(noticeRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> noticeService.getAnnouncementDetail(999L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
    verify(noticeRepository, never()).save(any());
  }
}
