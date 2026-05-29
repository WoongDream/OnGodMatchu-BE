package com.ongodmatchu.domain.quiz.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizAttempt;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
    return quizRepository.save(
        Quiz.builder().user(owner).title("풀이 집계 퀴즈").category("general").build());
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
}
