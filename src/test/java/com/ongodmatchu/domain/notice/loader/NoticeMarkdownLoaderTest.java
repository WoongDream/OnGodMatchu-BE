package com.ongodmatchu.domain.notice.loader;

import static org.assertj.core.api.Assertions.assertThat;

import com.ongodmatchu.domain.notice.document.NoticeDocument;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoticeMarkdownLoaderTest {

  private NoticeMarkdownLoader loader;

  @BeforeEach
  void setUp() throws Exception {
    loader = new NoticeMarkdownLoader();
    loader.load();
  }

  @Test
  @DisplayName("load_classpath_announcements_파일파싱")
  void load_parsesAnnouncementsFromClasspath() {
    List<NoticeDocument> all = loader.findAllAnnouncements();

    assertThat(all).isNotEmpty();
    NoticeDocument first = all.get(0);
    assertThat(first.slug()).isEqualTo("service-open");
    assertThat(first.title()).isEqualTo("온갓맞추 서비스 오픈 안내");
    assertThat(first.publishedAt()).isEqualTo(LocalDate.of(2026, 5, 27));
    assertThat(first.content()).contains("안녕하세요, 온갓맞추 입니다");
    assertThat(first.content()).doesNotContain("---");
  }

  @Test
  @DisplayName("findAnnouncementBySlug_존재slug_Optional반환")
  void findAnnouncementBySlug_existingSlug_returnsDocument() {
    Optional<NoticeDocument> doc = loader.findAnnouncementBySlug("service-open");

    assertThat(doc).isPresent();
    assertThat(doc.get().title()).isEqualTo("온갓맞추 서비스 오픈 안내");
  }

  @Test
  @DisplayName("findAnnouncementBySlug_미존재slug_Optional_empty")
  void findAnnouncementBySlug_unknownSlug_returnsEmpty() {
    Optional<NoticeDocument> doc = loader.findAnnouncementBySlug("does-not-exist");

    assertThat(doc).isEmpty();
  }
}
