package com.ongodmatchu.domain.inquiry.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ongodmatchu.domain.inquiry.entity.Inquiry;
import com.ongodmatchu.domain.inquiry.entity.InquiryStatus;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InquiryRepositoryTest {

  @Autowired private InquiryRepository inquiryRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager em;

  private User savedUser(String email, String nickname) {
    return userRepository.save(
        User.builder()
            .email(email)
            .nickname(nickname)
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build());
  }

  /**
   * 문의를 저장하고 status·createdAt 을 DB 에 직접 박는다. 빌더는 PENDING 으로만 생성하고, @CreatedDate auditing 은 동일 트랜잭션
   * 내 INSERT 들에 같은 시각을 부여할 수 있어 createdAt DESC 정렬 검증이 비결정적이 된다. 따라서 native UPDATE 로 상태와 접수 시각을
   * 확정한다.
   */
  private Inquiry savedInquiry(
      User user, String title, InquiryStatus status, LocalDateTime createdAt) {
    Inquiry inquiry =
        inquiryRepository.save(
            Inquiry.builder().user(user).title(title).content(title + " 본문").build());
    em.flush();
    em.createNativeQuery("UPDATE inquiries SET status = :status, created_at = :ts WHERE id = :id")
        .setParameter("status", status.name())
        .setParameter("ts", createdAt)
        .setParameter("id", inquiry.getId())
        .executeUpdate();
    return inquiry;
  }

  private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 0, 0);

  // ============ findByUserIdOrderByCreatedAtDesc — 본인 목록 ============

  @Test
  @DisplayName("findByUserId_본인_문의만_접수최신순으로_반환")
  void findByUserId_returnsOwnInquiriesOrderedByCreatedAtDesc() {
    User me = savedUser("me@example.com", "나");
    savedInquiry(me, "오래된 문의", InquiryStatus.PENDING, BASE.plusMinutes(1));
    savedInquiry(me, "중간 문의", InquiryStatus.PENDING, BASE.plusMinutes(2));
    savedInquiry(me, "최신 문의", InquiryStatus.PENDING, BASE.plusMinutes(3));
    em.flush();
    em.clear();

    Page<Inquiry> page = inquiryRepository.findByUserIdOrderByCreatedAtDesc(me.getId(), page(10));

    assertThat(page.getTotalElements()).isEqualTo(3L);
    assertThat(page.getContent())
        .extracting(Inquiry::getTitle)
        .containsExactly("최신 문의", "중간 문의", "오래된 문의");
  }

  @Test
  @DisplayName("findByUserId_타인_문의는_제외된다")
  void findByUserId_excludesOtherUsersInquiries() {
    User me = savedUser("me@example.com", "나");
    User other = savedUser("other@example.com", "다른사람");
    savedInquiry(me, "내 문의", InquiryStatus.PENDING, BASE.plusMinutes(1));
    savedInquiry(other, "남의 문의", InquiryStatus.PENDING, BASE.plusMinutes(2));
    em.flush();
    em.clear();

    Page<Inquiry> page = inquiryRepository.findByUserIdOrderByCreatedAtDesc(me.getId(), page(10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent()).extracting(Inquiry::getTitle).containsExactly("내 문의");
  }

  @Test
  @DisplayName("findByUserId_문의없으면_빈페이지")
  void findByUserId_noInquiries_returnsEmptyPage() {
    User me = savedUser("me@example.com", "나");

    Page<Inquiry> page = inquiryRepository.findByUserIdOrderByCreatedAtDesc(me.getId(), page(10));

    assertThat(page.getContent()).isEmpty();
    assertThat(page.getTotalElements()).isZero();
  }

  // ============ searchForAdmin — 백오피스 목록 ============

  @Test
  @DisplayName("searchForAdmin_status_q모두null이면_전체를_접수최신순으로_반환")
  void searchForAdmin_allNull_returnsAllOrderedByCreatedAtDesc() {
    User me = savedUser("me@example.com", "나");
    savedInquiry(me, "오래된", InquiryStatus.PENDING, BASE.plusMinutes(1));
    savedInquiry(me, "진행중인", InquiryStatus.IN_PROGRESS, BASE.plusMinutes(2));
    savedInquiry(me, "완료된", InquiryStatus.DONE, BASE.plusMinutes(3));
    em.flush();
    em.clear();

    Page<Inquiry> page = inquiryRepository.searchForAdmin(null, null, page(10));

    assertThat(page.getTotalElements()).isEqualTo(3L);
    assertThat(page.getContent())
        .extracting(Inquiry::getTitle)
        .containsExactly("완료된", "진행중인", "오래된");
  }

  @Test
  @DisplayName("searchForAdmin_status필터_PENDING만_조회")
  void searchForAdmin_statusPending_onlyPending() {
    User me = savedUser("me@example.com", "나");
    Inquiry pending = savedInquiry(me, "접수", InquiryStatus.PENDING, BASE.plusMinutes(1));
    savedInquiry(me, "진행중", InquiryStatus.IN_PROGRESS, BASE.plusMinutes(2));
    savedInquiry(me, "완료", InquiryStatus.DONE, BASE.plusMinutes(3));
    em.flush();
    em.clear();

    Page<Inquiry> page = inquiryRepository.searchForAdmin(InquiryStatus.PENDING, null, page(10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent().get(0).getId()).isEqualTo(pending.getId());
  }

  @Test
  @DisplayName("searchForAdmin_status필터_IN_PROGRESS만_조회")
  void searchForAdmin_statusInProgress_onlyInProgress() {
    User me = savedUser("me@example.com", "나");
    savedInquiry(me, "접수", InquiryStatus.PENDING, BASE.plusMinutes(1));
    Inquiry inProgress = savedInquiry(me, "진행중", InquiryStatus.IN_PROGRESS, BASE.plusMinutes(2));
    savedInquiry(me, "완료", InquiryStatus.DONE, BASE.plusMinutes(3));
    em.flush();
    em.clear();

    Page<Inquiry> page =
        inquiryRepository.searchForAdmin(InquiryStatus.IN_PROGRESS, null, page(10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent().get(0).getId()).isEqualTo(inProgress.getId());
  }

  @Test
  @DisplayName("searchForAdmin_status필터_DONE만_조회")
  void searchForAdmin_statusDone_onlyDone() {
    User me = savedUser("me@example.com", "나");
    savedInquiry(me, "접수", InquiryStatus.PENDING, BASE.plusMinutes(1));
    savedInquiry(me, "진행중", InquiryStatus.IN_PROGRESS, BASE.plusMinutes(2));
    Inquiry done = savedInquiry(me, "완료", InquiryStatus.DONE, BASE.plusMinutes(3));
    em.flush();
    em.clear();

    Page<Inquiry> page = inquiryRepository.searchForAdmin(InquiryStatus.DONE, null, page(10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent().get(0).getId()).isEqualTo(done.getId());
  }

  @Test
  @DisplayName("searchForAdmin_q는_제목_대소문자무시_부분일치_null이면_전체")
  void searchForAdmin_titleFilterCaseInsensitiveAndNullReturnsAll() {
    User me = savedUser("me@example.com", "나");
    Inquiry login = savedInquiry(me, "Login Error", InquiryStatus.PENDING, BASE.plusMinutes(1));
    savedInquiry(me, "Payment Issue", InquiryStatus.PENDING, BASE.plusMinutes(2));
    em.flush();
    em.clear();

    // 소문자 부분 검색어로도 대문자 제목이 매칭돼야 함
    Page<Inquiry> filtered = inquiryRepository.searchForAdmin(null, "login", page(10));
    assertThat(filtered.getTotalElements()).isEqualTo(1L);
    assertThat(filtered.getContent().get(0).getId()).isEqualTo(login.getId());

    // null 이면 전체
    Page<Inquiry> all = inquiryRepository.searchForAdmin(null, null, page(10));
    assertThat(all.getTotalElements()).isEqualTo(2L);
  }

  @Test
  @DisplayName("searchForAdmin_status와_q_동시필터_교집합조회")
  void searchForAdmin_statusAndQuery_intersection() {
    User me = savedUser("me@example.com", "나");
    Inquiry target = savedInquiry(me, "결제 오류", InquiryStatus.IN_PROGRESS, BASE.plusMinutes(1));
    savedInquiry(me, "결제 문의", InquiryStatus.PENDING, BASE.plusMinutes(2));
    savedInquiry(me, "로그인 오류", InquiryStatus.IN_PROGRESS, BASE.plusMinutes(3));
    em.flush();
    em.clear();

    Page<Inquiry> page =
        inquiryRepository.searchForAdmin(InquiryStatus.IN_PROGRESS, "결제", page(10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent().get(0).getId()).isEqualTo(target.getId());
  }

  @Test
  @DisplayName("searchForAdmin_매칭없으면_빈페이지")
  void searchForAdmin_noMatch_returnsEmptyPage() {
    User me = savedUser("me@example.com", "나");
    savedInquiry(me, "기존 문의", InquiryStatus.PENDING, BASE.plusMinutes(1));
    em.flush();
    em.clear();

    Page<Inquiry> page = inquiryRepository.searchForAdmin(null, "없는키워드", page(10));

    assertThat(page.getTotalElements()).isZero();
    assertThat(page.getContent()).isEmpty();
  }

  // ============ countByStatus ============

  @Test
  @DisplayName("countByStatus_해당상태_문의수만_센다")
  void countByStatus_countsOnlyGivenStatus() {
    User me = savedUser("me@example.com", "나");
    savedInquiry(me, "접수1", InquiryStatus.PENDING, BASE.plusMinutes(1));
    savedInquiry(me, "접수2", InquiryStatus.PENDING, BASE.plusMinutes(2));
    savedInquiry(me, "진행중", InquiryStatus.IN_PROGRESS, BASE.plusMinutes(3));
    savedInquiry(me, "완료", InquiryStatus.DONE, BASE.plusMinutes(4));
    em.flush();
    em.clear();

    assertThat(inquiryRepository.countByStatus(InquiryStatus.PENDING)).isEqualTo(2L);
    assertThat(inquiryRepository.countByStatus(InquiryStatus.IN_PROGRESS)).isEqualTo(1L);
    assertThat(inquiryRepository.countByStatus(InquiryStatus.DONE)).isEqualTo(1L);
  }

  @Test
  @DisplayName("countByStatus_해당상태없으면_0")
  void countByStatus_none_returnsZero() {
    User me = savedUser("me@example.com", "나");
    savedInquiry(me, "접수", InquiryStatus.PENDING, BASE.plusMinutes(1));
    em.flush();
    em.clear();

    assertThat(inquiryRepository.countByStatus(InquiryStatus.DONE)).isZero();
  }

  private static Pageable page(int size) {
    return PageRequest.of(0, size);
  }
}
