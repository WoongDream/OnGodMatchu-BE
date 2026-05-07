package com.ongodmatchu.domain.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.quiz.repository.QuizStarRepository;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuizStarServiceTest {

  @InjectMocks private QuizStarService quizStarService;
  @Mock private QuizRepository quizRepository;
  @Mock private QuizStarRepository quizStarRepository;
  @Mock private UserRepository userRepository;

  private User testUser(Long id) {
    User user =
        User.builder()
            .email("u" + id + "@example.com")
            .nickname("user" + id)
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", id);
    ReflectionTestUtils.setField(user, "publicId", UUID.randomUUID());
    return user;
  }

  private Quiz testQuiz(User owner, QuizVisibility visibility) {
    Quiz quiz =
        Quiz.builder().user(owner).title("t").category("game").visibility(visibility).build();
    ReflectionTestUtils.setField(quiz, "id", 10L);
    return quiz;
  }

  @Test
  @DisplayName("star_PUBLIC퀴즈_최초호출시저장+카운터증가")
  void star_publicQuiz_firstCall_savesAndIncrements() {
    User owner = testUser(1L);
    User viewer = testUser(2L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizRepository.findById(10L)).willReturn(Optional.of(quiz));
    given(quizStarRepository.existsByUserIdAndQuizId(2L, 10L)).willReturn(false);
    given(userRepository.findById(2L)).willReturn(Optional.of(viewer));

    quizStarService.star(2L, 10L);

    assertThat(quiz.getStarCount()).isEqualTo(1);
    then(quizStarRepository).should().save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("star_이미좋아요누른상태_멱등_save미호출_카운터변동없음")
  void star_alreadyStarred_idempotent() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizRepository.findById(10L)).willReturn(Optional.of(quiz));
    given(quizStarRepository.existsByUserIdAndQuizId(2L, 10L)).willReturn(true);

    quizStarService.star(2L, 10L);

    assertThat(quiz.getStarCount()).isZero();
    then(quizStarRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("unstar_좋아요눌렀던상태_삭제+카운터감소")
  void unstar_existingStar_decrements() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    quiz.incrementStarCount();
    given(quizRepository.findById(10L)).willReturn(Optional.of(quiz));
    given(quizStarRepository.deleteByUserIdAndQuizId(2L, 10L)).willReturn(1L);

    quizStarService.unstar(2L, 10L);

    assertThat(quiz.getStarCount()).isZero();
  }

  @Test
  @DisplayName("unstar_좋아요누른적없음_멱등_카운터변동없음")
  void unstar_noExistingStar_idempotent() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizRepository.findById(10L)).willReturn(Optional.of(quiz));
    given(quizStarRepository.deleteByUserIdAndQuizId(2L, 10L)).willReturn(0L);

    quizStarService.unstar(2L, 10L);

    assertThat(quiz.getStarCount()).isZero();
  }

  @Test
  @DisplayName("star_PRIVATE퀴즈_외부유저_QUIZ_NOT_FOUND")
  void star_privateQuiz_externalUser_throws() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PRIVATE);
    given(quizRepository.findById(10L)).willReturn(Optional.of(quiz));

    assertThatThrownBy(() -> quizStarService.star(2L, 10L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
    then(quizStarRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("star_PRIVATE퀴즈_본인_정상저장")
  void star_privateQuiz_owner_succeeds() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PRIVATE);
    given(quizRepository.findById(10L)).willReturn(Optional.of(quiz));
    given(quizStarRepository.existsByUserIdAndQuizId(1L, 10L)).willReturn(false);
    given(userRepository.findById(1L)).willReturn(Optional.of(owner));

    quizStarService.star(1L, 10L);

    assertThat(quiz.getStarCount()).isEqualTo(1);
  }
}
