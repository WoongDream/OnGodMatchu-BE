package com.ongodmatchu.domain.visit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VisitLogRepositoryTest {

  @Autowired private VisitLogRepository visitLogRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager em;

  @BeforeEach
  void cleanUp() {
    // 사전 시드/커밋된 행이 카운트를 오염시키지 않도록 격리
    em.createNativeQuery("DELETE FROM visit_log").executeUpdate();
  }

  /** anon_id 기반(비로그인) 방문 1건을 지정한 시각으로 직접 삽입. */
  private void insertAnonVisit(String anonId, LocalDateTime visitedAt) {
    em.createNativeQuery(
            "INSERT INTO visit_log (anon_id, user_id, path, visited_at) VALUES (?, NULL, ?, ?)")
        .setParameter(1, anonId)
        .setParameter(2, "/")
        .setParameter(3, visitedAt)
        .executeUpdate();
  }

  /** user_id 기반(로그인) 방문 1건을 지정한 시각으로 직접 삽입. */
  private void insertUserVisit(Long userId, String anonId, LocalDateTime visitedAt) {
    em.createNativeQuery(
            "INSERT INTO visit_log (anon_id, user_id, path, visited_at) VALUES (?, ?, ?, ?)")
        .setParameter(1, anonId)
        .setParameter(2, userId)
        .setParameter(3, "/")
        .setParameter(4, visitedAt)
        .executeUpdate();
  }

  @Test
  @DisplayName("빈 테이블이면 누적 방문자 수는 0이다")
  void countTotalVisitors_emptyTable_returnsZero() {
    assertThat(visitLogRepository.countTotalVisitors()).isZero();
  }

  @Test
  @DisplayName("같은 방문자가 서로 다른 두 날 방문하면 날마다 1씩 합산되어 2다")
  void countTotalVisitors_sameVisitorTwoDays_countsPerDay() {
    LocalDateTime yesterday = LocalDate.now().minusDays(1).atTime(10, 0);
    LocalDateTime today = LocalDate.now().atTime(10, 0);
    insertAnonVisit("anon-A", yesterday);
    insertAnonVisit("anon-A", today);

    assertThat(visitLogRepository.countTotalVisitors()).isEqualTo(2);
  }

  @Test
  @DisplayName("같은 방문자가 같은 날 두 번 방문하면 1로 집계된다")
  void countTotalVisitors_sameVisitorSameDay_countsOnce() {
    LocalDateTime today = LocalDate.now().atTime(9, 0);
    insertAnonVisit("anon-A", today);
    insertAnonVisit("anon-A", today.plusHours(3));

    assertThat(visitLogRepository.countTotalVisitors()).isEqualTo(1);
  }

  @Test
  @DisplayName("어제 고유 방문자 4명 + 오늘 고유 방문자 3명 = 누적 7명")
  void countTotalVisitors_distinctVisitorsAcrossTwoDays_sumsDaily() {
    LocalDateTime yesterday = LocalDate.now().minusDays(1).atTime(11, 0);
    LocalDateTime today = LocalDate.now().atTime(11, 0);

    insertAnonVisit("y-1", yesterday);
    insertAnonVisit("y-2", yesterday);
    insertAnonVisit("y-3", yesterday);
    insertAnonVisit("y-4", yesterday);

    insertAnonVisit("t-1", today);
    insertAnonVisit("t-2", today);
    insertAnonVisit("t-3", today);

    assertThat(visitLogRepository.countTotalVisitors()).isEqualTo(7);
  }

  @Test
  @DisplayName("로그인(user_id)·비로그인(anon_id) 혼재 시 COALESCE 식별이 올바르게 합산된다")
  void countTotalVisitors_mixedUserAndAnon_identifiesCorrectly() {
    User user =
        userRepository.save(
            User.builder()
                .email("visit-test@example.com")
                .nickname("방문자")
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .build());
    em.flush();

    LocalDateTime today = LocalDate.now().atTime(12, 0);

    // 같은 로그인 사용자의 같은 날 2건 방문 → user_id 기준 1로 집계
    insertUserVisit(user.getId(), "anon-x", today);
    insertUserVisit(user.getId(), "anon-y", today.plusHours(1));
    // 비로그인 방문자 1명
    insertAnonVisit("anon-z", today);

    // 같은 날: 로그인 사용자 1 + 비로그인 1 = 2
    assertThat(visitLogRepository.countTotalVisitors()).isEqualTo(2);
  }
}
