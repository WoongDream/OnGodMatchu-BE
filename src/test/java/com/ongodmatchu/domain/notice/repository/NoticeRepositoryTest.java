package com.ongodmatchu.domain.notice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ongodmatchu.domain.notice.entity.Notice;
import com.ongodmatchu.domain.notice.entity.NoticeType;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NoticeRepositoryTest {

  @Autowired private NoticeRepository noticeRepository;
  @Autowired private EntityManager em;

  @BeforeEach
  void clearSeed() {
    noticeRepository.deleteAllInBatch();
    em.flush();
    em.clear();
  }

  private Notice save(NoticeType type, String title, LocalDateTime publishedAt) {
    Notice n =
        Notice.builder().type(type).title(title).content("body").publishedAt(publishedAt).build();
    return noticeRepository.save(n);
  }

  @Test
  @DisplayName("ANNOUNCEMENT 타입만 정확히 필터링되어 반환된다")
  void findByType_announcement_onlyMatchingType() {
    save(NoticeType.ANNOUNCEMENT, "공지1", LocalDateTime.now());
    save(NoticeType.ANNOUNCEMENT, "공지2", LocalDateTime.now());
    save(NoticeType.RELEASE_NOTE, "릴리즈1", LocalDateTime.now());
    em.flush();
    em.clear();

    Page<Notice> result =
        noticeRepository.findByTypeAndPublishedAtIsNotNull(
            NoticeType.ANNOUNCEMENT, PageRequest.of(0, 10));

    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getContent()).allMatch(n -> n.getType() == NoticeType.ANNOUNCEMENT);
  }

  @Test
  @DisplayName("RELEASE_NOTE 타입만 정확히 필터링되어 반환된다")
  void findByType_releaseNote_onlyMatchingType() {
    save(NoticeType.ANNOUNCEMENT, "공지1", LocalDateTime.now());
    save(NoticeType.RELEASE_NOTE, "릴리즈1", LocalDateTime.now());
    save(NoticeType.RELEASE_NOTE, "릴리즈2", LocalDateTime.now());
    em.flush();
    em.clear();

    Page<Notice> result =
        noticeRepository.findByTypeAndPublishedAtIsNotNull(
            NoticeType.RELEASE_NOTE, PageRequest.of(0, 10));

    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getContent()).allMatch(n -> n.getType() == NoticeType.RELEASE_NOTE);
  }

  @Test
  @DisplayName("publishedAt 이 NULL 인 미발행 공지는 제외된다")
  void findByType_excludesUnpublished() {
    save(NoticeType.ANNOUNCEMENT, "발행됨", LocalDateTime.now());
    save(NoticeType.ANNOUNCEMENT, "미발행", null);
    em.flush();
    em.clear();

    Page<Notice> result =
        noticeRepository.findByTypeAndPublishedAtIsNotNull(
            NoticeType.ANNOUNCEMENT, PageRequest.of(0, 10));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getTitle()).isEqualTo("발행됨");
  }

  @Test
  @DisplayName("publishedAt DESC 정렬 Pageable 주입 시 최신 발행순으로 반환된다")
  void findByType_orderByPublishedAtDesc() {
    LocalDateTime base = LocalDateTime.of(2025, 1, 1, 0, 0);
    save(NoticeType.ANNOUNCEMENT, "오래된 공지", base);
    save(NoticeType.ANNOUNCEMENT, "최신 공지", base.plusDays(2));
    save(NoticeType.ANNOUNCEMENT, "중간 공지", base.plusDays(1));
    em.flush();
    em.clear();

    Pageable pageable = PageRequest.of(0, 10, Sort.by(Direction.DESC, "publishedAt"));
    Page<Notice> result =
        noticeRepository.findByTypeAndPublishedAtIsNotNull(NoticeType.ANNOUNCEMENT, pageable);

    assertThat(result.getContent()).hasSize(3);
    assertThat(result.getContent().get(0).getTitle()).isEqualTo("최신 공지");
    assertThat(result.getContent().get(1).getTitle()).isEqualTo("중간 공지");
    assertThat(result.getContent().get(2).getTitle()).isEqualTo("오래된 공지");
  }

  @Test
  @DisplayName("데이터가 없으면 빈 Page 를 반환한다")
  void findByType_emptyResult() {
    Page<Notice> result =
        noticeRepository.findByTypeAndPublishedAtIsNotNull(
            NoticeType.ANNOUNCEMENT, PageRequest.of(0, 10));

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
  }

  @Test
  @DisplayName("발행된 공지의 id+type 이 일치하면 Optional.present 를 반환한다")
  void findByIdAndType_happyPath() {
    Notice saved = save(NoticeType.ANNOUNCEMENT, "발행된 공지", LocalDateTime.now());
    em.flush();
    em.clear();

    Optional<Notice> result =
        noticeRepository.findByIdAndTypeAndPublishedAtIsNotNull(
            saved.getId(), NoticeType.ANNOUNCEMENT);

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(saved.getId());
    assertThat(result.get().getTitle()).isEqualTo("발행된 공지");
  }

  @Test
  @DisplayName("같은 id 라도 type 이 다르면 empty 를 반환한다")
  void findByIdAndType_typeMismatch() {
    Notice saved = save(NoticeType.ANNOUNCEMENT, "공지", LocalDateTime.now());
    em.flush();
    em.clear();

    Optional<Notice> result =
        noticeRepository.findByIdAndTypeAndPublishedAtIsNotNull(
            saved.getId(), NoticeType.RELEASE_NOTE);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("publishedAt 이 NULL 이면 empty 를 반환한다")
  void findByIdAndType_unpublished() {
    Notice saved = save(NoticeType.ANNOUNCEMENT, "미발행", null);
    em.flush();
    em.clear();

    Optional<Notice> result =
        noticeRepository.findByIdAndTypeAndPublishedAtIsNotNull(
            saved.getId(), NoticeType.ANNOUNCEMENT);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("존재하지 않는 id 로 조회하면 empty 를 반환한다")
  void findByIdAndType_idNotFound() {
    Optional<Notice> result =
        noticeRepository.findByIdAndTypeAndPublishedAtIsNotNull(999_999L, NoticeType.ANNOUNCEMENT);

    assertThat(result).isEmpty();
  }
}
