package com.ongodmatchu.domain.quiz.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class QuizRepositoryTest {

  @Autowired private QuizRepository quizRepository;
  @Autowired private QuestionRepository questionRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager em;

  private User savedUser() {
    return userRepository.save(
        User.builder()
            .email("quiz-test@example.com")
            .nickname("퀴즈작성자")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build());
  }

  @Test
  @DisplayName("퀴즈를 저장하고 조회한다")
  void saveAndFind() {
    User user = savedUser();
    Quiz quiz =
        Quiz.builder().user(user).title("한국사 퀴즈").category("general").description("한국사 기초").build();
    quizRepository.save(quiz);

    Quiz found = quizRepository.findById(quiz.getId()).orElseThrow();
    assertThat(found.getTitle()).isEqualTo("한국사 퀴즈");
    assertThat(found.getPlayCount()).isZero();
    assertThat(found.getPublicId()).isNotNull();
  }

  @Test
  @DisplayName("플레이 카운트가 1 증가한다")
  void incrementPlayCount() {
    User user = savedUser();
    Quiz quiz = Quiz.builder().user(user).title("팝송 퀴즈").category("music").build();
    quizRepository.save(quiz);

    quiz.incrementPlayCount();
    quizRepository.save(quiz);

    Quiz found = quizRepository.findById(quiz.getId()).orElseThrow();
    assertThat(found.getPlayCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("문제를 순서대로 조회한다")
  void findQuestionsByOrderNum() {
    User user = savedUser();
    Quiz quiz = Quiz.builder().user(user).title("순서 퀴즈").category("general").build();
    quizRepository.save(quiz);

    questionRepository.save(Question.builder().quiz(quiz).orderNum(2).answer("두번째").build());
    questionRepository.save(Question.builder().quiz(quiz).orderNum(1).answer("첫번째").build());

    List<Question> questions = questionRepository.findByQuizIdOrderByOrderNum(quiz.getId());

    assertThat(questions).hasSize(2);
    assertThat(questions.get(0).getAnswer()).isEqualTo("첫번째");
    assertThat(questions.get(1).getAnswer()).isEqualTo("두번째");
  }

  @Test
  @DisplayName("문제의 imageKey / answerImageKey 를 저장하고 조회한다")
  void saveAndFindImageKeys() {
    User user = savedUser();
    Quiz quiz = Quiz.builder().user(user).title("이미지 퀴즈").category("game").build();
    quizRepository.save(quiz);

    questionRepository.save(
        Question.builder()
            .quiz(quiz)
            .orderNum(1)
            .imageKey("quiz-images/uid/q.png")
            .answerImageKey("quiz-images/uid/a.png")
            .answer("정답")
            .build());

    List<Question> questions = questionRepository.findByQuizIdOrderByOrderNum(quiz.getId());
    assertThat(questions).hasSize(1);
    assertThat(questions.get(0).getImageKey()).isEqualTo("quiz-images/uid/q.png");
    assertThat(questions.get(0).getAnswerImageKey()).isEqualTo("quiz-images/uid/a.png");
  }

  @Test
  @DisplayName("sumPlayCountByCategory — 같은 카테고리 PUBLIC 플레이수 합산 + PRIVATE 제외")
  void sumPlayCountByCategory_groupsAndFiltersByVisibility() {
    User user = savedUser();

    // game: PUBLIC 2건 합산 (3 + 2 = 5)
    Quiz gameA = Quiz.builder().user(user).title("게임A").category("game").build();
    gameA.changeVisibility(QuizVisibility.PUBLIC);
    gameA.incrementPlayCount();
    gameA.incrementPlayCount();
    gameA.incrementPlayCount();
    quizRepository.save(gameA);

    Quiz gameB = Quiz.builder().user(user).title("게임B").category("game").build();
    gameB.changeVisibility(QuizVisibility.PUBLIC);
    gameB.incrementPlayCount();
    gameB.incrementPlayCount();
    quizRepository.save(gameB);

    // music: PUBLIC 1건 (7)
    Quiz music = Quiz.builder().user(user).title("음악").category("music").build();
    music.changeVisibility(QuizVisibility.PUBLIC);
    for (int i = 0; i < 7; i++) {
      music.incrementPlayCount();
    }
    quizRepository.save(music);

    // game: PRIVATE 1건 (100) — 집계에서 제외돼야 함
    Quiz gamePrivate = Quiz.builder().user(user).title("게임비공개").category("game").build();
    gamePrivate.changeVisibility(QuizVisibility.PRIVATE);
    for (int i = 0; i < 100; i++) {
      gamePrivate.incrementPlayCount();
    }
    quizRepository.save(gamePrivate);

    em.flush();
    em.clear();

    List<QuizRepository.CategoryPlayCountRow> rows =
        quizRepository.sumPlayCountByCategory(QuizVisibility.PUBLIC);

    Map<String, Long> byCategory =
        rows.stream()
            .collect(
                Collectors.toMap(
                    QuizRepository.CategoryPlayCountRow::getCategory,
                    QuizRepository.CategoryPlayCountRow::getPlays));

    // PRIVATE 100 은 제외되고 PUBLIC 만 합산
    assertThat(byCategory).containsEntry("game", 5L).containsEntry("music", 7L);
    assertThat(byCategory).doesNotContainValue(105L);
  }

  @Test
  @DisplayName("User 저장 시 publicId 가 자동 발급된다")
  void userPublicIdAssigned() {
    User user = savedUser();
    assertThat(user.getPublicId()).isNotNull();
  }

  /**
   * 회원탈퇴 회귀 가드 — `transferOwnership` 의 @Modifying 이 영속 컨텍스트를 clear 하면 호출자가 미리 로드한 User 인스턴스까지
   * detach 되어 `user.withdraw(...)` dirty checking 이 무시되고 users 행 UPDATE 가 누락됨 (실제 운영 버그). 본 테스트는
   * transferOwnership 호출 후에도 사전 로드한 user 가 영속 상태를 유지하며 필드 변경이 DB 에 반영되는지 검증.
   */
  @Test
  @DisplayName("transferOwnership 호출 후에도 사전 로드한 user dirty checking 이 반영된다")
  void transferOwnership_keepsExternallyLoadedUserManaged() {
    User owner = savedUser();
    User admin =
        userRepository.save(
            User.builder()
                .email("transfer-admin@test.local")
                .nickname("이전관리자")
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .build());
    Quiz quiz = Quiz.builder().user(owner).title("이전 대상").category("general").build();
    quizRepository.save(quiz);

    em.flush();
    em.clear();

    User reloaded = userRepository.findById(owner.getId()).orElseThrow();
    assertThat(em.contains(reloaded)).isTrue();

    int moved = quizRepository.transferOwnership(owner.getId(), admin.getId());
    assertThat(moved).isEqualTo(1);

    assertThat(em.contains(reloaded)).as("transferOwnership 호출이 영속 컨텍스트를 clear 하면 안 된다").isTrue();

    reloaded.updateNickname("탈퇴익명");
    em.flush();
    em.clear();

    User refound = userRepository.findById(owner.getId()).orElseThrow();
    assertThat(refound.getNickname()).isEqualTo("탈퇴익명");
  }
}
