package com.ongodmatchu.domain.notice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ongodmatchu.domain.notice.entity.Notice;
import com.ongodmatchu.domain.notice.entity.NoticeStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NoticeRepositoryTest {

  @Autowired private NoticeRepository noticeRepository;
  @Autowired private EntityManager em;

  // 테스트 DB 에 남은 행과 격리 — 각 테스트는 빈 notices 에서 시작.
  @BeforeEach
  void clean() {
    noticeRepository.deleteAllInBatch();
    em.flush();
    em.clear();
  }

  private Notice savedNotice(String title, NoticeStatus status, boolean pinned) {
    Notice notice =
        Notice.builder().title(title).content(title + " 본문").status(status).pinned(pinned).build();
    return noticeRepository.save(notice);
  }

  // ============ findByStatusOrderByPinnedDescPublishedAtDesc — 공개 목록 ============

  @Test
  @DisplayName("findByStatus_PUBLISHED만_노출하고_DRAFT는_제외된다")
  void findByStatus_onlyPublished() {
    savedNotice("게시 공지", NoticeStatus.PUBLISHED, false);
    savedNotice("임시저장 공지", NoticeStatus.DRAFT, false);
    em.flush();

    Page<Notice> page =
        noticeRepository.findByStatusOrderByPinnedDescPublishedAtDesc(
            NoticeStatus.PUBLISHED, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent().get(0).getTitle()).isEqualTo("게시 공지");
  }

  @Test
  @DisplayName("findByStatus_고정공지가_먼저오고_그다음_게시시각_최신순")
  void findByStatus_pinnedFirstThenPublishedAtDesc() {
    // 영속화 순서로 publishedAt(now())을 통제: 먼저 저장될수록 더 과거.
    Notice oldNormal = savedNotice("오래된 게시", NoticeStatus.PUBLISHED, false);
    em.flush();
    Notice pinned = savedNotice("고정 공지", NoticeStatus.PUBLISHED, true);
    em.flush();
    Notice newNormal = savedNotice("최신 게시", NoticeStatus.PUBLISHED, false);
    em.flush();

    Page<Notice> page =
        noticeRepository.findByStatusOrderByPinnedDescPublishedAtDesc(
            NoticeStatus.PUBLISHED, PageRequest.of(0, 10));

    // 고정 우선 → 미고정 중 최신 게시순(newNormal, oldNormal)
    assertThat(page.getContent())
        .extracting(Notice::getId)
        .containsExactly(pinned.getId(), newNormal.getId(), oldNormal.getId());
  }

  @Test
  @DisplayName("findByStatus_게시공지없으면_빈페이지")
  void findByStatus_empty() {
    savedNotice("임시저장만", NoticeStatus.DRAFT, false);
    em.flush();

    Page<Notice> page =
        noticeRepository.findByStatusOrderByPinnedDescPublishedAtDesc(
            NoticeStatus.PUBLISHED, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isZero();
    assertThat(page.getContent()).isEmpty();
  }

  // ============ searchForAdmin — 백오피스 목록 ============

  @Test
  @DisplayName("searchForAdmin_status_pinned_q모두null이면_전체조회")
  void searchForAdmin_allNull_returnsAll() {
    savedNotice("게시 고정", NoticeStatus.PUBLISHED, true);
    savedNotice("게시 미고정", NoticeStatus.PUBLISHED, false);
    savedNotice("임시저장", NoticeStatus.DRAFT, false);
    em.flush();

    Page<Notice> page = noticeRepository.searchForAdmin(null, null, null, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(3L);
  }

  @Test
  @DisplayName("searchForAdmin_PUBLISHED_pinnedTrue이면_고정만조회")
  void searchForAdmin_publishedPinned_onlyPinned() {
    Notice pinned = savedNotice("게시 고정", NoticeStatus.PUBLISHED, true);
    savedNotice("게시 미고정", NoticeStatus.PUBLISHED, false);
    savedNotice("임시저장", NoticeStatus.DRAFT, false);
    em.flush();

    Page<Notice> page =
        noticeRepository.searchForAdmin(NoticeStatus.PUBLISHED, true, null, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent().get(0).getId()).isEqualTo(pinned.getId());
  }

  @Test
  @DisplayName("searchForAdmin_PUBLISHED_pinnedNull이면_고정포함_게시전체조회 (게시 필터)")
  void searchForAdmin_publishedPinnedNull_includesPinned() {
    Notice pinned = savedNotice("게시 고정", NoticeStatus.PUBLISHED, true);
    Notice unpinned = savedNotice("게시 미고정", NoticeStatus.PUBLISHED, false);
    savedNotice("임시저장", NoticeStatus.DRAFT, false);
    em.flush();

    Page<Notice> page =
        noticeRepository.searchForAdmin(NoticeStatus.PUBLISHED, null, null, PageRequest.of(0, 10));

    assertThat(page.getContent())
        .extracting(Notice::getId)
        .containsExactlyInAnyOrder(pinned.getId(), unpinned.getId());
  }

  @Test
  @DisplayName("searchForAdmin_PUBLISHED_pinnedFalse이면_게시미고정만조회")
  void searchForAdmin_publishedUnpinned_onlyUnpinned() {
    savedNotice("게시 고정", NoticeStatus.PUBLISHED, true);
    Notice unpinned = savedNotice("게시 미고정", NoticeStatus.PUBLISHED, false);
    savedNotice("임시저장", NoticeStatus.DRAFT, false);
    em.flush();

    Page<Notice> page =
        noticeRepository.searchForAdmin(NoticeStatus.PUBLISHED, false, null, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent().get(0).getId()).isEqualTo(unpinned.getId());
  }

  @Test
  @DisplayName("searchForAdmin_DRAFT이면_임시저장만조회")
  void searchForAdmin_draft_onlyDraft() {
    savedNotice("게시 고정", NoticeStatus.PUBLISHED, true);
    savedNotice("게시 미고정", NoticeStatus.PUBLISHED, false);
    Notice draft = savedNotice("임시저장", NoticeStatus.DRAFT, false);
    em.flush();

    Page<Notice> page =
        noticeRepository.searchForAdmin(NoticeStatus.DRAFT, null, null, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent().get(0).getId()).isEqualTo(draft.getId());
  }

  @Test
  @DisplayName("searchForAdmin_q는_제목_대소문자무시_부분일치_null이면_전체")
  void searchForAdmin_titleFilterCaseInsensitive() {
    Notice update = savedNotice("Update Notice", NoticeStatus.PUBLISHED, false);
    savedNotice("Maintenance", NoticeStatus.PUBLISHED, false);
    em.flush();

    // 대소문자 무시 부분일치
    Page<Notice> filtered =
        noticeRepository.searchForAdmin(null, null, "update", PageRequest.of(0, 10));
    assertThat(filtered.getTotalElements()).isEqualTo(1L);
    assertThat(filtered.getContent().get(0).getId()).isEqualTo(update.getId());

    // null 이면 전체
    Page<Notice> all = noticeRepository.searchForAdmin(null, null, null, PageRequest.of(0, 10));
    assertThat(all.getTotalElements()).isEqualTo(2L);
  }

  @Test
  @DisplayName("searchForAdmin_고정우선_작성최신순_정렬")
  void searchForAdmin_pinnedFirstThenCreatedAtDesc() {
    Notice oldNormal = savedNotice("오래된 미고정", NoticeStatus.PUBLISHED, false);
    em.flush();
    Notice pinned = savedNotice("고정", NoticeStatus.PUBLISHED, true);
    em.flush();
    Notice newNormal = savedNotice("최신 미고정", NoticeStatus.PUBLISHED, false);
    em.flush();

    Page<Notice> page = noticeRepository.searchForAdmin(null, null, null, PageRequest.of(0, 10));

    assertThat(page.getContent())
        .extracting(Notice::getId)
        .containsExactly(pinned.getId(), newNormal.getId(), oldNormal.getId());
  }

  @Test
  @DisplayName("searchForAdmin_매칭없으면_빈페이지")
  void searchForAdmin_noMatch_empty() {
    savedNotice("게시 공지", NoticeStatus.PUBLISHED, false);
    em.flush();

    Page<Notice> page = noticeRepository.searchForAdmin(null, null, "없는키워드", PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isZero();
  }

  // ============ countByStatus / countByStatusAndPinned ============

  @Test
  @DisplayName("countByStatus_DRAFT_임시저장수만_센다")
  void countByStatus_draft() {
    savedNotice("임시1", NoticeStatus.DRAFT, false);
    savedNotice("임시2", NoticeStatus.DRAFT, false);
    savedNotice("게시", NoticeStatus.PUBLISHED, false);
    em.flush();

    assertThat(noticeRepository.countByStatus(NoticeStatus.DRAFT)).isEqualTo(2L);
  }

  @Test
  @DisplayName("countByStatus_해당상태없으면_0")
  void countByStatus_none_returnsZero() {
    savedNotice("게시", NoticeStatus.PUBLISHED, false);
    em.flush();

    assertThat(noticeRepository.countByStatus(NoticeStatus.DRAFT)).isZero();
  }

  @Test
  @DisplayName("countByStatusAndPinned_PUBLISHED_pinned분기별_집계")
  void countByStatusAndPinned_publishedByPinned() {
    savedNotice("고정1", NoticeStatus.PUBLISHED, true);
    savedNotice("고정2", NoticeStatus.PUBLISHED, true);
    savedNotice("미고정", NoticeStatus.PUBLISHED, false);
    savedNotice("임시 고정아님", NoticeStatus.DRAFT, false);
    em.flush();

    assertThat(noticeRepository.countByStatusAndPinned(NoticeStatus.PUBLISHED, true)).isEqualTo(2L);
    assertThat(noticeRepository.countByStatusAndPinned(NoticeStatus.PUBLISHED, false))
        .isEqualTo(1L);
  }
}
