package com.ongodmatchu.domain.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.ongodmatchu.domain.notice.document.NoticeDocument;
import com.ongodmatchu.domain.notice.dto.NoticeDetailResponse;
import com.ongodmatchu.domain.notice.dto.NoticeListItemResponse;
import com.ongodmatchu.domain.notice.loader.NoticeMarkdownLoader;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

  @InjectMocks private NoticeService noticeService;
  @Mock private NoticeMarkdownLoader loader;

  private NoticeDocument doc(String slug, String title, LocalDate publishedAt) {
    return new NoticeDocument(slug, title, "## " + title + "\n\n본문 " + slug, publishedAt);
  }

  // ============ getAnnouncements ============

  @Test
  @DisplayName("getAnnouncements_정상_NoticeListItemResponse매핑")
  void getAnnouncements_success_mapsToResponse() {
    NoticeDocument d = doc("service-open", "공지 제목", LocalDate.of(2026, 1, 1));
    given(loader.findAllAnnouncements()).willReturn(List.of(d));

    Page<NoticeListItemResponse> result = noticeService.getAnnouncements(PageRequest.of(0, 20));

    assertThat(result.getContent()).hasSize(1);
    NoticeListItemResponse item = result.getContent().get(0);
    assertThat(item.slug()).isEqualTo("service-open");
    assertThat(item.title()).isEqualTo("공지 제목");
    assertThat(item.publishedAt()).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("getAnnouncements_size100요청_50으로cap")
  void getAnnouncements_pageSize_cappedAt50() {
    List<NoticeDocument> docs =
        java.util.stream.IntStream.range(0, 60)
            .mapToObj(i -> doc("slug-" + i, "제목 " + i, LocalDate.of(2026, 1, 1).minusDays(i)))
            .toList();
    given(loader.findAllAnnouncements()).willReturn(docs);

    Page<NoticeListItemResponse> result = noticeService.getAnnouncements(PageRequest.of(0, 100));

    assertThat(result.getSize()).isEqualTo(50);
    assertThat(result.getContent()).hasSize(50);
    assertThat(result.getTotalElements()).isEqualTo(60);
  }

  @Test
  @DisplayName("getAnnouncements_size_0_20으로디폴트")
  void getAnnouncements_pageSize_zero_defaultsTo20() {
    given(loader.findAllAnnouncements()).willReturn(List.of());

    Page<NoticeListItemResponse> result = noticeService.getAnnouncements(zeroSizePageable());

    assertThat(result.getSize()).isEqualTo(20);
  }

  @Test
  @DisplayName("getAnnouncements_size_5_정상범위_그대로유지")
  void getAnnouncements_pageSize_withinRange_keepsAsIs() {
    given(loader.findAllAnnouncements()).willReturn(List.of());

    Page<NoticeListItemResponse> result = noticeService.getAnnouncements(PageRequest.of(0, 5));

    assertThat(result.getSize()).isEqualTo(5);
  }

  @Test
  @DisplayName("getAnnouncements_2페이지_offset맞춰_슬라이스")
  void getAnnouncements_secondPage_slicesByOffset() {
    List<NoticeDocument> docs =
        List.of(
            doc("d1", "1", LocalDate.of(2026, 1, 5)),
            doc("d2", "2", LocalDate.of(2026, 1, 4)),
            doc("d3", "3", LocalDate.of(2026, 1, 3)),
            doc("d4", "4", LocalDate.of(2026, 1, 2)),
            doc("d5", "5", LocalDate.of(2026, 1, 1)));
    given(loader.findAllAnnouncements()).willReturn(docs);

    Page<NoticeListItemResponse> page2 = noticeService.getAnnouncements(PageRequest.of(1, 2));

    assertThat(page2.getContent())
        .extracting(NoticeListItemResponse::slug)
        .containsExactly("d3", "d4");
    assertThat(page2.getTotalElements()).isEqualTo(5);
    assertThat(page2.getNumber()).isEqualTo(1);
  }

  @Test
  @DisplayName("getAnnouncements_빈리스트_빈페이지")
  void getAnnouncements_emptyLoader_returnsEmptyPage() {
    given(loader.findAllAnnouncements()).willReturn(List.of());

    Page<NoticeListItemResponse> result = noticeService.getAnnouncements(PageRequest.of(0, 20));

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
  }

  @Test
  @DisplayName("getAnnouncements_offset_total초과_빈콘텐츠")
  void getAnnouncements_offsetBeyondTotal_returnsEmptyContent() {
    given(loader.findAllAnnouncements())
        .willReturn(List.of(doc("only", "1", LocalDate.of(2026, 1, 1))));

    Page<NoticeListItemResponse> page = noticeService.getAnnouncements(PageRequest.of(5, 20));

    assertThat(page.getContent()).isEmpty();
    assertThat(page.getTotalElements()).isEqualTo(1);
  }

  // ============ getAnnouncementDetail ============

  @Test
  @DisplayName("getAnnouncementDetail_정상_NoticeDetailResponse매핑")
  void getAnnouncementDetail_success_mapsToResponse() {
    NoticeDocument d = doc("service-open", "공지 제목", LocalDate.of(2026, 1, 1));
    given(loader.findAnnouncementBySlug("service-open")).willReturn(Optional.of(d));

    NoticeDetailResponse result = noticeService.getAnnouncementDetail("service-open");

    assertThat(result.slug()).isEqualTo("service-open");
    assertThat(result.title()).isEqualTo("공지 제목");
    assertThat(result.content()).contains("본문 service-open");
    assertThat(result.publishedAt()).isNotNull();
  }

  @Test
  @DisplayName("getAnnouncementDetail_loader_empty_NOTICE_NOT_FOUND예외")
  void getAnnouncementDetail_notFound_throwsBusinessException() {
    given(loader.findAnnouncementBySlug("missing")).willReturn(Optional.empty());

    assertThatThrownBy(() -> noticeService.getAnnouncementDetail("missing"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
  }

  private static Pageable zeroSizePageable() {
    return new Pageable() {
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
  }
}
