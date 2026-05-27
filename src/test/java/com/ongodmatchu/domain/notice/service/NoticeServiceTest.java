package com.ongodmatchu.domain.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.ongodmatchu.domain.notice.dto.NoticeDetailResponse;
import com.ongodmatchu.domain.notice.dto.NoticeListItemResponse;
import com.ongodmatchu.domain.notice.entity.Notice;
import com.ongodmatchu.domain.notice.entity.NoticeType;
import com.ongodmatchu.domain.notice.repository.NoticeRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.time.LocalDateTime;
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
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

  @InjectMocks private NoticeService noticeService;
  @Mock private NoticeRepository noticeRepository;

  private Notice testNotice() {
    Notice notice =
        Notice.builder()
            .type(NoticeType.ANNOUNCEMENT)
            .title("공지 제목")
            .content("공지 본문")
            .publishedAt(LocalDateTime.of(2026, 1, 1, 12, 0))
            .build();
    ReflectionTestUtils.setField(notice, "id", 1L);
    return notice;
  }

  // ============ getList ============

  @Test
  @DisplayName("getList_정상_NoticeListItemResponse매핑")
  void getList_success_mapsToResponse() {
    Notice notice = testNotice();
    given(
            noticeRepository.findByTypeAndPublishedAtIsNotNull(
                eq(NoticeType.ANNOUNCEMENT), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(notice)));

    Page<NoticeListItemResponse> result =
        noticeService.getList(NoticeType.ANNOUNCEMENT, PageRequest.of(0, 20));

    assertThat(result.getContent()).hasSize(1);
    NoticeListItemResponse item = result.getContent().get(0);
    assertThat(item.id()).isEqualTo(1L);
    assertThat(item.title()).isEqualTo("공지 제목");
    assertThat(item.publishedAt()).isNotNull();
  }

  @Test
  @DisplayName("getList_정렬강제_publishedAt_DESC_id_DESC")
  void getList_enforcesSortByPublishedAtDescAndIdDesc() {
    given(
            noticeRepository.findByTypeAndPublishedAtIsNotNull(
                eq(NoticeType.ANNOUNCEMENT), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    noticeService.getList(NoticeType.ANNOUNCEMENT, PageRequest.of(0, 20));

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(noticeRepository)
        .should()
        .findByTypeAndPublishedAtIsNotNull(eq(NoticeType.ANNOUNCEMENT), captor.capture());

    Sort sort = captor.getValue().getSort();
    Sort.Order publishedAtOrder = sort.getOrderFor("publishedAt");
    Sort.Order idOrder = sort.getOrderFor("id");
    assertThat(publishedAtOrder).isNotNull();
    assertThat(publishedAtOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
    assertThat(idOrder).isNotNull();
    assertThat(idOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  @DisplayName("getList_size100요청_50으로cap")
  void getList_pageSize_cappedAt50() {
    given(
            noticeRepository.findByTypeAndPublishedAtIsNotNull(
                eq(NoticeType.ANNOUNCEMENT), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    noticeService.getList(NoticeType.ANNOUNCEMENT, PageRequest.of(0, 100));

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(noticeRepository)
        .should()
        .findByTypeAndPublishedAtIsNotNull(eq(NoticeType.ANNOUNCEMENT), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(50);
  }

  @Test
  @DisplayName("getList_size_0_20으로디폴트")
  void getList_pageSize_zero_defaultsTo20() {
    given(
            noticeRepository.findByTypeAndPublishedAtIsNotNull(
                eq(NoticeType.ANNOUNCEMENT), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    Pageable zeroSize =
        new Pageable() {
          @Override
          public int getPageNumber() {
            return 0;
          }

          @Override
          public int getPageSize() {
            return 0;
          }

          @Override
          public long getOffset() {
            return 0;
          }

          @Override
          public Sort getSort() {
            return Sort.unsorted();
          }

          @Override
          public Pageable next() {
            return this;
          }

          @Override
          public Pageable previousOrFirst() {
            return this;
          }

          @Override
          public Pageable first() {
            return this;
          }

          @Override
          public Pageable withPage(int pageNumber) {
            return this;
          }

          @Override
          public boolean hasPrevious() {
            return false;
          }
        };

    noticeService.getList(NoticeType.ANNOUNCEMENT, zeroSize);

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(noticeRepository)
        .should()
        .findByTypeAndPublishedAtIsNotNull(eq(NoticeType.ANNOUNCEMENT), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(20);
  }

  @Test
  @DisplayName("getList_size_5_정상범위_그대로유지")
  void getList_pageSize_withinRange_keepsAsIs() {
    given(
            noticeRepository.findByTypeAndPublishedAtIsNotNull(
                eq(NoticeType.ANNOUNCEMENT), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    noticeService.getList(NoticeType.ANNOUNCEMENT, PageRequest.of(0, 5));

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(noticeRepository)
        .should()
        .findByTypeAndPublishedAtIsNotNull(eq(NoticeType.ANNOUNCEMENT), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(5);
  }

  @Test
  @DisplayName("getList_type_ANNOUNCEMENT_그대로전달")
  void getList_announcementType_passedThrough() {
    given(
            noticeRepository.findByTypeAndPublishedAtIsNotNull(
                eq(NoticeType.ANNOUNCEMENT), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    noticeService.getList(NoticeType.ANNOUNCEMENT, PageRequest.of(0, 20));

    then(noticeRepository)
        .should()
        .findByTypeAndPublishedAtIsNotNull(eq(NoticeType.ANNOUNCEMENT), any(Pageable.class));
  }

  @Test
  @DisplayName("getList_type_RELEASE_NOTE_그대로전달")
  void getList_releaseNoteType_passedThrough() {
    given(
            noticeRepository.findByTypeAndPublishedAtIsNotNull(
                eq(NoticeType.RELEASE_NOTE), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of()));

    noticeService.getList(NoticeType.RELEASE_NOTE, PageRequest.of(0, 20));

    then(noticeRepository)
        .should()
        .findByTypeAndPublishedAtIsNotNull(eq(NoticeType.RELEASE_NOTE), any(Pageable.class));
  }

  // ============ getDetail ============

  @Test
  @DisplayName("getDetail_정상_NoticeDetailResponse매핑")
  void getDetail_success_mapsToResponse() {
    Notice notice = testNotice();
    given(noticeRepository.findByIdAndTypeAndPublishedAtIsNotNull(1L, NoticeType.ANNOUNCEMENT))
        .willReturn(Optional.of(notice));

    NoticeDetailResponse result = noticeService.getDetail(NoticeType.ANNOUNCEMENT, 1L);

    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.title()).isEqualTo("공지 제목");
    assertThat(result.content()).isEqualTo("공지 본문");
    assertThat(result.publishedAt()).isNotNull();
  }

  @Test
  @DisplayName("getDetail_repository_empty_NOTICE_NOT_FOUND예외")
  void getDetail_notFound_throwsBusinessException() {
    given(noticeRepository.findByIdAndTypeAndPublishedAtIsNotNull(99L, NoticeType.ANNOUNCEMENT))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> noticeService.getDetail(NoticeType.ANNOUNCEMENT, 99L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
  }
}
