package com.ongodmatchu.domain.quiz.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import java.util.List;
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
    Quiz quiz = Quiz.builder().user(user).title("순서 퀴즈").category("etc").build();
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
    Quiz quiz = Quiz.builder().user(user).title("이미지 퀴즈").category("entertainment").build();
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
  @DisplayName("User 저장 시 publicId 가 자동 발급된다")
  void userPublicIdAssigned() {
    User user = savedUser();
    assertThat(user.getPublicId()).isNotNull();
  }
}
