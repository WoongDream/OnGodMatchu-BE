package com.ongodmatchu.domain.quiz.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizStar;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
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
class QuizStarRepositoryTest {

  @Autowired private QuizStarRepository quizStarRepository;
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

  private Quiz savedQuiz(User owner, String title, QuizVisibility visibility) {
    Quiz quiz = Quiz.builder().user(owner).title(title).category("general").build();
    quiz.changeVisibility(visibility);
    return quizRepository.save(quiz);
  }

  /**
   * 스타를 저장하고 createdAt 을 명시적으로 지정한다. @CreatedDate auditing 은 동일 트랜잭션 내 INSERT 들에 동일 시각을 부여할 수 있어
   * createdAt DESC 정렬 검증이 비결정적이 된다. created_at 은 updatable=false 라 엔티티 dirty checking 으로는 갱신되지
   * 않으므로, native UPDATE 로 DB 값을 직접 박아 순서를 확정한다.
   */
  private QuizStar saveStar(User user, Quiz quiz, LocalDateTime starredAt) {
    QuizStar star = quizStarRepository.save(QuizStar.builder().user(user).quiz(quiz).build());
    em.flush();
    em.createNativeQuery("UPDATE quiz_stars SET created_at = :ts WHERE id = :id")
        .setParameter("ts", starredAt)
        .setParameter("id", star.getId())
        .executeUpdate();
    return star;
  }

  @Test
  @DisplayName("findStarredQuizzesByUserId_내가스타준퀴즈만_스타시각DESC로_반환")
  void findStarredQuizzesByUserId_returnsMyStarsOrderedByCreatedAtDesc() {
    User me = savedUser("me@example.com", "나");
    Quiz first = savedQuiz(me, "첫번째", QuizVisibility.PUBLIC);
    Quiz second = savedQuiz(me, "두번째", QuizVisibility.PUBLIC);
    Quiz third = savedQuiz(me, "세번째", QuizVisibility.PUBLIC);

    LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
    saveStar(me, first, base.plusMinutes(1));
    saveStar(me, second, base.plusMinutes(2));
    saveStar(me, third, base.plusMinutes(3));
    em.flush();
    em.clear();

    Page<Quiz> page = quizStarRepository.findStarredQuizzesByUserId(me.getId(), null, page(10));

    assertThat(page.getTotalElements()).isEqualTo(3L);
    // 가장 최근에 스타 누른 third → second → first 순
    assertThat(page.getContent()).extracting(Quiz::getTitle).containsExactly("세번째", "두번째", "첫번째");
  }

  @Test
  @DisplayName("findStarredQuizzesByUserId_내가스타준_PRIVATE퀴즈도_포함된다")
  void findStarredQuizzesByUserId_includesOwnStarredPrivateQuizzes() {
    User me = savedUser("me@example.com", "나");
    Quiz publicQuiz = savedQuiz(me, "공개", QuizVisibility.PUBLIC);
    Quiz privateQuiz = savedQuiz(me, "비공개", QuizVisibility.PRIVATE);

    LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
    saveStar(me, publicQuiz, base.plusMinutes(1));
    saveStar(me, privateQuiz, base.plusMinutes(2));
    em.flush();
    em.clear();

    Page<Quiz> page = quizStarRepository.findStarredQuizzesByUserId(me.getId(), null, page(10));

    assertThat(page.getTotalElements()).isEqualTo(2L);
    assertThat(page.getContent()).extracting(Quiz::getTitle).containsExactly("비공개", "공개");
  }

  @Test
  @DisplayName("findStarredQuizzesByUserId_title부분일치_대소문자무시_필터")
  void findStarredQuizzesByUserId_titleLikeCaseInsensitive() {
    User me = savedUser("me@example.com", "나");
    Quiz kpop = savedQuiz(me, "KPOP 퀴즈", QuizVisibility.PUBLIC);
    Quiz history = savedQuiz(me, "한국사 퀴즈", QuizVisibility.PUBLIC);

    LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
    saveStar(me, kpop, base.plusMinutes(1));
    saveStar(me, history, base.plusMinutes(2));
    em.flush();
    em.clear();

    // 소문자 검색어로도 대문자 제목이 매칭돼야 함
    Page<Quiz> page = quizStarRepository.findStarredQuizzesByUserId(me.getId(), "kpop", page(10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent()).extracting(Quiz::getTitle).containsExactly("KPOP 퀴즈");
  }

  @Test
  @DisplayName("findStarredQuizzesByUserId_title_null이면_전체반환")
  void findStarredQuizzesByUserId_nullTitleReturnsAll() {
    User me = savedUser("me@example.com", "나");
    Quiz a = savedQuiz(me, "A 퀴즈", QuizVisibility.PUBLIC);
    Quiz b = savedQuiz(me, "B 퀴즈", QuizVisibility.PUBLIC);

    LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
    saveStar(me, a, base.plusMinutes(1));
    saveStar(me, b, base.plusMinutes(2));
    em.flush();
    em.clear();

    Page<Quiz> page = quizStarRepository.findStarredQuizzesByUserId(me.getId(), null, page(10));

    assertThat(page.getTotalElements()).isEqualTo(2L);
  }

  @Test
  @DisplayName("findStarredQuizzesByUserId_다른유저가스타준퀴즈는_제외된다")
  void findStarredQuizzesByUserId_excludesOtherUsersStars() {
    User me = savedUser("me@example.com", "나");
    User other = savedUser("other@example.com", "다른사람");
    Quiz mine = savedQuiz(me, "내가스타", QuizVisibility.PUBLIC);
    Quiz theirs = savedQuiz(me, "남이스타", QuizVisibility.PUBLIC);

    LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
    saveStar(me, mine, base.plusMinutes(1));
    saveStar(other, theirs, base.plusMinutes(2));
    em.flush();
    em.clear();

    Page<Quiz> page = quizStarRepository.findStarredQuizzesByUserId(me.getId(), null, page(10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent()).extracting(Quiz::getTitle).containsExactly("내가스타");
  }

  @Test
  @DisplayName("findStarredQuizzesByUserId_스타없음_빈페이지")
  void findStarredQuizzesByUserId_noStars_returnsEmptyPage() {
    User me = savedUser("me@example.com", "나");

    Page<Quiz> page = quizStarRepository.findStarredQuizzesByUserId(me.getId(), null, page(10));

    assertThat(page.getContent()).isEmpty();
    assertThat(page.getTotalElements()).isZero();
  }

  private static Pageable page(int size) {
    return PageRequest.of(0, size);
  }
}
