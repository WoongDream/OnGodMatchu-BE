package com.ongodmatchu.domain.quiz.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizAttempt;
import com.ongodmatchu.domain.quiz.repository.QuizAttemptRepository.AttemptGroupRow;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
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
class QuizAttemptRepositoryTest {

  @Autowired private QuizAttemptRepository quizAttemptRepository;
  @Autowired private QuizRepository quizRepository;
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

  private Quiz savedQuiz(User owner) {
    return savedQuiz(owner, "풀이 집계 퀴즈");
  }

  private Quiz savedQuiz(User owner, String title) {
    return quizRepository.save(Quiz.builder().user(owner).title(title).category("general").build());
  }

  private void saveAttempt(User user, Quiz quiz, int score, int totalQuestions) {
    quizAttemptRepository.save(
        QuizAttempt.builder()
            .user(user)
            .quiz(quiz)
            .score(score)
            .totalQuestions(totalQuestions)
            .build());
  }

  @Test
  @DisplayName("countByUserId_본인user의attempts수를_센다")
  void countByUserId_countsAttemptsOfUser() {
    User solver = savedUser("solver@example.com", "푸는사람");
    Quiz quiz = savedQuiz(solver);

    saveAttempt(solver, quiz, 3, 5);
    saveAttempt(solver, quiz, 4, 5);
    saveAttempt(solver, quiz, 2, 5);
    em.flush();

    assertThat(quizAttemptRepository.countByUserId(solver.getId())).isEqualTo(3L);
  }

  @Test
  @DisplayName("countByUserId_비로그인(user=null)attempt는_제외된다")
  void countByUserId_excludesAnonymousAttempts() {
    User solver = savedUser("solver@example.com", "푸는사람");
    Quiz quiz = savedQuiz(solver);

    saveAttempt(solver, quiz, 3, 5);
    saveAttempt(null, quiz, 1, 5); // 비로그인 풀이
    saveAttempt(null, quiz, 5, 5); // 비로그인 풀이
    em.flush();

    assertThat(quizAttemptRepository.countByUserId(solver.getId())).isEqualTo(1L);
  }

  @Test
  @DisplayName("countByUserId_다른user의attempt는_세지않는다")
  void countByUserId_doesNotCountOtherUsers() {
    User solver = savedUser("solver@example.com", "푸는사람");
    User other = savedUser("other@example.com", "다른사람");
    Quiz quiz = savedQuiz(solver);

    saveAttempt(solver, quiz, 3, 5);
    saveAttempt(other, quiz, 4, 5);
    saveAttempt(other, quiz, 2, 5);
    em.flush();

    assertThat(quizAttemptRepository.countByUserId(solver.getId())).isEqualTo(1L);
  }

  @Test
  @DisplayName("countByUserId_attempt없으면_0")
  void countByUserId_noAttempts_returnsZero() {
    User solver = savedUser("solver@example.com", "푸는사람");

    assertThat(quizAttemptRepository.countByUserId(solver.getId())).isZero();
  }

  @Test
  @DisplayName("avgSolveRateOf_SUM(score)*100/SUM(totalQuestions)_정상산출")
  void avgSolveRateOf_aggregatesAcrossAttempts() {
    User solver = savedUser("solver@example.com", "푸는사람");
    Quiz quiz = savedQuiz(solver);

    // SUM(score)=10, SUM(totalQuestions)=20 → 10*100/20 = 50.0
    saveAttempt(solver, quiz, 4, 10);
    saveAttempt(solver, quiz, 6, 10);
    em.flush();

    assertThat(quizAttemptRepository.avgSolveRateOf(solver.getId())).isEqualTo(50.0);
  }

  @Test
  @DisplayName("avgSolveRateOf_본인attempt만_집계하고_타인은_제외")
  void avgSolveRateOf_onlyOwnAttempts() {
    User solver = savedUser("solver@example.com", "푸는사람");
    User other = savedUser("other@example.com", "다른사람");
    Quiz quiz = savedQuiz(solver);

    // 본인: SUM(score)=8, SUM(totalQuestions)=10 → 80.0
    saveAttempt(solver, quiz, 8, 10);
    // 타인 attempt 는 산출에 영향 없어야 함
    saveAttempt(other, quiz, 0, 10);
    em.flush();

    assertThat(quizAttemptRepository.avgSolveRateOf(solver.getId())).isEqualTo(80.0);
  }

  @Test
  @DisplayName("avgSolveRateOf_시도0이면_null")
  void avgSolveRateOf_noAttempts_returnsNull() {
    User solver = savedUser("solver@example.com", "푸는사람");

    assertThat(quizAttemptRepository.avgSolveRateOf(solver.getId())).isNull();
  }

  @Test
  @DisplayName("findAttemptGroupsByUserId_quiz당1행으로_누적횟수를_집계한다")
  void findAttemptGroupsByUserId_groupsByQuizWithCounts() {
    User solver = savedUser("solver@example.com", "푸는사람");
    Quiz quizA = savedQuiz(solver, "퀴즈 A");
    Quiz quizB = savedQuiz(solver, "퀴즈 B");

    saveAttempt(solver, quizA, 3, 5);
    saveAttempt(solver, quizA, 4, 5);
    saveAttempt(solver, quizA, 2, 5);
    saveAttempt(solver, quizB, 5, 5);
    em.flush();

    Page<AttemptGroupRow> page =
        quizAttemptRepository.findAttemptGroupsByUserId(
            solver.getId(), null, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(2L);
    assertThat(page.getContent())
        .extracting(AttemptGroupRow::getQuizId, AttemptGroupRow::getAttemptCount)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(quizA.getId(), 3L),
            org.assertj.core.groups.Tuple.tuple(quizB.getId(), 1L));
  }

  @Test
  @DisplayName("findAttemptGroupsByUserId_최신풀이퀴즈가_먼저온다")
  void findAttemptGroupsByUserId_ordersByRecencyDesc() {
    User solver = savedUser("solver@example.com", "푸는사람");
    Quiz quizOld = savedQuiz(solver, "오래된 퀴즈");
    Quiz quizNew = savedQuiz(solver, "최신 퀴즈");

    // completedAt 은 @PrePersist 가 now() 로 채우고 updatable=false 라 명시 설정 불가.
    // → 영속화 순서(시간 순)로 통제: quizOld 먼저 flush, 이후 quizNew 를 풀어 MAX(completedAt) 이 더 크게 만든다.
    saveAttempt(solver, quizOld, 1, 5);
    em.flush();
    saveAttempt(solver, quizNew, 1, 5);
    em.flush();

    Page<AttemptGroupRow> page =
        quizAttemptRepository.findAttemptGroupsByUserId(
            solver.getId(), null, PageRequest.of(0, 10));

    assertThat(page.getContent()).extracting(AttemptGroupRow::getQuizId).hasSize(2);
    assertThat(page.getContent().get(0).getQuizId()).isEqualTo(quizNew.getId());
  }

  @Test
  @DisplayName("findAttemptGroupsByUserId_title필터는_대소문자무시_부분일치_null이면_전체")
  void findAttemptGroupsByUserId_titleFilterCaseInsensitive() {
    User solver = savedUser("solver@example.com", "푸는사람");
    Quiz java = savedQuiz(solver, "Java Quiz");
    Quiz python = savedQuiz(solver, "Python Quiz");

    saveAttempt(solver, java, 1, 5);
    saveAttempt(solver, python, 1, 5);
    em.flush();

    // 대소문자 무시 부분일치
    Page<AttemptGroupRow> filtered =
        quizAttemptRepository.findAttemptGroupsByUserId(
            solver.getId(), "java", PageRequest.of(0, 10));
    assertThat(filtered.getTotalElements()).isEqualTo(1L);
    assertThat(filtered.getContent().get(0).getQuizId()).isEqualTo(java.getId());

    // null 이면 필터 없이 전체
    Page<AttemptGroupRow> all =
        quizAttemptRepository.findAttemptGroupsByUserId(
            solver.getId(), null, PageRequest.of(0, 10));
    assertThat(all.getTotalElements()).isEqualTo(2L);
  }

  @Test
  @DisplayName("findAttemptGroupsByUserId_타인과_비로그인(user=null)attempt는_제외된다")
  void findAttemptGroupsByUserId_excludesOtherUsersAndAnonymous() {
    User solver = savedUser("solver@example.com", "푸는사람");
    User other = savedUser("other@example.com", "다른사람");
    Quiz quiz = savedQuiz(solver, "공용 퀴즈");

    saveAttempt(solver, quiz, 2, 5);
    saveAttempt(other, quiz, 3, 5); // 타인
    saveAttempt(null, quiz, 4, 5); // 비로그인
    em.flush();

    Page<AttemptGroupRow> page =
        quizAttemptRepository.findAttemptGroupsByUserId(
            solver.getId(), null, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent().get(0).getAttemptCount()).isEqualTo(1L);
  }

  @Test
  @DisplayName("findLatestAttemptsPerQuiz_quiz당_MAX(id)attempt1건씩_미응시quiz는_누락")
  void findLatestAttemptsPerQuiz_returnsLatestPerQuiz() {
    User solver = savedUser("solver@example.com", "푸는사람");
    Quiz quizA = savedQuiz(solver, "퀴즈 A");
    Quiz quizB = savedQuiz(solver, "퀴즈 B");
    Quiz unplayed = savedQuiz(solver, "미응시 퀴즈");

    saveAttempt(solver, quizA, 1, 5);
    saveAttempt(solver, quizA, 2, 5);
    saveAttempt(solver, quizA, 3, 5); // quizA 최신
    saveAttempt(solver, quizB, 4, 5);
    saveAttempt(solver, quizB, 5, 5); // quizB 최신
    em.flush();

    List<QuizAttempt> latest =
        quizAttemptRepository.findLatestAttemptsPerQuiz(
            solver.getId(), List.of(quizA.getId(), quizB.getId(), unplayed.getId()));

    assertThat(latest)
        .extracting(a -> a.getQuiz().getId(), QuizAttempt::getScore)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(quizA.getId(), 3),
            org.assertj.core.groups.Tuple.tuple(quizB.getId(), 5));
    // 미응시 quiz 는 누락
    assertThat(latest).extracting(a -> a.getQuiz().getId()).doesNotContain(unplayed.getId());
  }

  @Test
  @DisplayName("timeLimitSec_topPercentile_라운드트립_저장후_조회시_보존된다")
  void roundTrip_timeLimitAndTopPercentile() {
    User solver = savedUser("solver@example.com", "푸는사람");
    Quiz quiz = savedQuiz(solver);

    QuizAttempt attempt =
        quizAttemptRepository.save(
            QuizAttempt.builder()
                .user(solver)
                .quiz(quiz)
                .score(3)
                .totalQuestions(5)
                .timeLimitSec(10)
                .build());
    attempt.assignTopPercentile(4.0);
    em.flush();
    em.clear();

    QuizAttempt reloaded = quizAttemptRepository.findById(attempt.getId()).orElseThrow();
    assertThat(reloaded.getTimeLimitSec()).isEqualTo(10);
    assertThat(reloaded.getTopPercentile()).isEqualTo(4.0);
  }
}
