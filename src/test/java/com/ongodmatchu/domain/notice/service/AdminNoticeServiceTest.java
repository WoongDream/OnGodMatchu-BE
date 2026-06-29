package com.ongodmatchu.domain.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminNoticeServiceTest {

  @InjectMocks private AdminNoticeService adminNoticeService;
  @Mock private NoticeRepository noticeRepository;

  private Notice notice(long id, String title, NoticeStatus status, boolean pinned) {
    Notice n =
        Notice.builder().title(title).content("본문 " + id).status(status).pinned(pinned).build();
    ReflectionTestUtils.setField(n, "id", id);
    return n;
  }

  private ErrorCode errorCodeOf(Throwable t) {
    return ((BusinessException) t).getErrorCode();
  }

  // ============ getNotices ============

  @Test
  @DisplayName("getNotices — PINNED 필터는 (PUBLISHED, true) 를 위임하고 결과를 매핑한다")
  void getNotices_pinnedFilter_delegatesAndMaps() {
    Notice n = notice(1L, "고정공지", NoticeStatus.PUBLISHED, true);
    given(noticeRepository.searchForAdmin(any(), any(), any(), any()))
        .willReturn(new PageImpl<>(List.of(n)));

    Page<AdminNoticeListItemResponse> result =
        adminNoticeService.getNotices(NoticeFilter.PINNED, "고정", PageRequest.of(0, 20));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).id()).isEqualTo(1L);
    assertThat(result.getContent().get(0).title()).isEqualTo("고정공지");
    assertThat(result.getContent().get(0).status()).isEqualTo("PUBLISHED");
    assertThat(result.getContent().get(0).pinned()).isTrue();

    ArgumentCaptor<NoticeStatus> statusCaptor = ArgumentCaptor.forClass(NoticeStatus.class);
    ArgumentCaptor<Boolean> pinnedCaptor = ArgumentCaptor.forClass(Boolean.class);
    then(noticeRepository)
        .should()
        .searchForAdmin(statusCaptor.capture(), pinnedCaptor.capture(), eq("고정"), any());
    assertThat(statusCaptor.getValue()).isEqualTo(NoticeStatus.PUBLISHED);
    assertThat(pinnedCaptor.getValue()).isEqualTo(Boolean.TRUE);
  }

  @Test
  @DisplayName("getNotices — PUBLISHED 필터는 (PUBLISHED, null) 을 위임한다 (고정 포함)")
  void getNotices_publishedFilter_delegates() {
    given(noticeRepository.searchForAdmin(any(), isNull(), any(), any()))
        .willReturn(new PageImpl<>(List.of()));

    adminNoticeService.getNotices(NoticeFilter.PUBLISHED, "x", PageRequest.of(0, 20));

    then(noticeRepository)
        .should()
        .searchForAdmin(eq(NoticeStatus.PUBLISHED), isNull(), eq("x"), any());
  }

  @Test
  @DisplayName("getNotices — DRAFT 필터는 (DRAFT, null) 을 위임한다")
  void getNotices_draftFilter_delegates() {
    given(noticeRepository.searchForAdmin(any(), isNull(), any(), any()))
        .willReturn(new PageImpl<>(List.of()));

    adminNoticeService.getNotices(NoticeFilter.DRAFT, "x", PageRequest.of(0, 20));

    then(noticeRepository)
        .should()
        .searchForAdmin(eq(NoticeStatus.DRAFT), isNull(), eq("x"), any());
  }

  @Test
  @DisplayName("getNotices — ALL 필터는 (null, null) 을 위임한다")
  void getNotices_allFilter_delegates() {
    given(noticeRepository.searchForAdmin(isNull(), isNull(), any(), any()))
        .willReturn(new PageImpl<>(List.of()));

    adminNoticeService.getNotices(NoticeFilter.ALL, "x", PageRequest.of(0, 20));

    then(noticeRepository).should().searchForAdmin(isNull(), isNull(), eq("x"), any());
  }

  @Test
  @DisplayName("getNotices — 빈 query 는 null 로 정규화되어 위임된다")
  void getNotices_blankQuery_normalizedToNull() {
    given(noticeRepository.searchForAdmin(isNull(), isNull(), isNull(), any()))
        .willReturn(new PageImpl<>(List.of()));

    adminNoticeService.getNotices(NoticeFilter.ALL, "   ", PageRequest.of(0, 20));

    then(noticeRepository).should().searchForAdmin(isNull(), isNull(), isNull(), any());
  }

  @Test
  @DisplayName("getNotices — pageSize 50 초과는 50 으로 cap, query 는 trim 후 위임")
  void getNotices_pageSizeCappedAndQueryTrimmed() {
    given(noticeRepository.searchForAdmin(isNull(), isNull(), eq("hong"), any()))
        .willReturn(new PageImpl<>(List.of()));

    adminNoticeService.getNotices(NoticeFilter.ALL, "  hong  ", PageRequest.of(0, 200));

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(noticeRepository)
        .should()
        .searchForAdmin(isNull(), isNull(), eq("hong"), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(50);
  }

  // ============ getStats ============

  @Test
  @DisplayName("getStats — count 결과들을 NoticeStatsResponse 로 매핑한다")
  void getStats_mapsCounts() {
    given(noticeRepository.count()).willReturn(10L);
    given(noticeRepository.countByStatus(NoticeStatus.PUBLISHED)).willReturn(7L);
    given(noticeRepository.countByStatusAndPinned(NoticeStatus.PUBLISHED, true)).willReturn(2L);
    given(noticeRepository.countByStatus(NoticeStatus.DRAFT)).willReturn(3L);

    NoticeStatsResponse result = adminNoticeService.getStats();

    assertThat(result.total()).isEqualTo(10L);
    // 게시 = 게시 전체(고정 포함)
    assertThat(result.published()).isEqualTo(7L);
    assertThat(result.pinned()).isEqualTo(2L);
    assertThat(result.draft()).isEqualTo(3L);
  }

  // ============ getNotice ============

  @Test
  @DisplayName("getNotice — 존재하면 AdminNoticeResponse 로 반환한다")
  void getNotice_found() {
    Notice n = notice(7L, "공지7", NoticeStatus.PUBLISHED, false);
    given(noticeRepository.findById(7L)).willReturn(Optional.of(n));

    AdminNoticeResponse result = adminNoticeService.getNotice(7L);

    assertThat(result.id()).isEqualTo(7L);
    assertThat(result.title()).isEqualTo("공지7");
    assertThat(result.status()).isEqualTo("PUBLISHED");
  }

  @Test
  @DisplayName("getNotice — 미존재 시 NOTICE_NOT_FOUND")
  void getNotice_notFound() {
    given(noticeRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> adminNoticeService.getNotice(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
  }

  // ============ create ============

  @Test
  @DisplayName("create — status null 이면 DRAFT 로 저장되고 publishedAt 은 null")
  void create_statusNull_savesAsDraft() {
    given(noticeRepository.save(any(Notice.class))).willAnswer(inv -> inv.getArgument(0));

    AdminNoticeResponse result =
        adminNoticeService.create(new NoticeCreateRequest("새 공지", "본문", null, false));

    ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
    then(noticeRepository).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(NoticeStatus.DRAFT);
    assertThat(captor.getValue().getPublishedAt()).isNull();
    assertThat(result.status()).isEqualTo("DRAFT");
    assertThat(result.publishedAt()).isNull();
  }

  @Test
  @DisplayName("create — status=PUBLISHED 면 publishedAt 이 세팅된다")
  void create_published_setsPublishedAt() {
    given(noticeRepository.save(any(Notice.class))).willAnswer(inv -> inv.getArgument(0));

    AdminNoticeResponse result =
        adminNoticeService.create(
            new NoticeCreateRequest("게시 공지", "본문", NoticeStatus.PUBLISHED, true));

    ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
    then(noticeRepository).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(NoticeStatus.PUBLISHED);
    assertThat(captor.getValue().getPublishedAt()).isNotNull();
    assertThat(captor.getValue().isPinned()).isTrue();
    assertThat(result.status()).isEqualTo("PUBLISHED");
    assertThat(result.publishedAt()).isNotNull();
  }

  // ============ update ============

  @Test
  @DisplayName("update — title/content 변경 및 DRAFT→PUBLISHED 전환 시 publishedAt 세팅")
  void update_changesFieldsAndPublishes() {
    Notice n = notice(3L, "원제목", NoticeStatus.DRAFT, false);
    given(noticeRepository.findById(3L)).willReturn(Optional.of(n));

    AdminNoticeResponse result =
        adminNoticeService.update(
            3L, new NoticeUpdateRequest("새제목", "새본문", NoticeStatus.PUBLISHED, true));

    assertThat(n.getTitle()).isEqualTo("새제목");
    assertThat(n.getContent()).isEqualTo("새본문");
    assertThat(n.getStatus()).isEqualTo(NoticeStatus.PUBLISHED);
    assertThat(n.getPublishedAt()).isNotNull();
    assertThat(n.isPinned()).isTrue();
    assertThat(result.title()).isEqualTo("새제목");
    assertThat(result.status()).isEqualTo("PUBLISHED");
  }

  @Test
  @DisplayName("update — title/content/status/pinned 모두 null 이면 미변경(부분 수정)")
  void update_allNull_noChange() {
    Notice n = notice(4L, "그대로", NoticeStatus.PUBLISHED, true);
    given(noticeRepository.findById(4L)).willReturn(Optional.of(n));

    adminNoticeService.update(4L, new NoticeUpdateRequest(null, null, null, null));

    assertThat(n.getTitle()).isEqualTo("그대로");
    assertThat(n.getContent()).isEqualTo("본문 4");
    assertThat(n.getStatus()).isEqualTo(NoticeStatus.PUBLISHED);
    assertThat(n.isPinned()).isTrue();
  }

  @Test
  @DisplayName("update — pinned 만 false 로 변경하면 pinned 만 바뀐다")
  void update_onlyPinned() {
    Notice n = notice(5L, "제목5", NoticeStatus.PUBLISHED, true);
    given(noticeRepository.findById(5L)).willReturn(Optional.of(n));

    adminNoticeService.update(5L, new NoticeUpdateRequest(null, null, null, false));

    assertThat(n.isPinned()).isFalse();
    assertThat(n.getTitle()).isEqualTo("제목5");
    assertThat(n.getStatus()).isEqualTo(NoticeStatus.PUBLISHED);
  }

  @Test
  @DisplayName("update — 미존재 시 NOTICE_NOT_FOUND")
  void update_notFound() {
    given(noticeRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> adminNoticeService.update(99L, new NoticeUpdateRequest("t", "c", null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
  }

  // ============ delete ============

  @Test
  @DisplayName("delete — 존재하면 repository.delete 호출")
  void delete_success() {
    Notice n = notice(6L, "삭제대상", NoticeStatus.DRAFT, false);
    given(noticeRepository.findById(6L)).willReturn(Optional.of(n));

    adminNoticeService.delete(6L);

    then(noticeRepository).should().delete(n);
  }

  @Test
  @DisplayName("delete — 미존재 시 NOTICE_NOT_FOUND, delete 미호출")
  void delete_notFound() {
    given(noticeRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> adminNoticeService.delete(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
    then(noticeRepository).should(never()).delete(any());
  }
}
