package com.ongodmatchu.domain.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.quiz.dto.QuizShareResponse;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizShare;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.quiz.repository.QuizShareRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuizShareServiceTest {

  @InjectMocks private QuizShareService quizShareService;
  @Mock private QuizRepository quizRepository;
  @Mock private QuizShareRepository quizShareRepository;
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
        Quiz.builder().user(owner).title("t").category("music").visibility(visibility).build();
    ReflectionTestUtils.setField(quiz, "id", 1L);
    ReflectionTestUtils.setField(quiz, "shareCount", 5);
    return quiz;
  }

  @Test
  @DisplayName("recordShare_로그인사용자_PUBLIC퀴즈_신규공유_saveAndFlush호출+카운트증가")
  void recordShare_loginUser_publicQuiz_firstShare() {
    User owner = testUser(1L);
    User viewer = testUser(2L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(quizShareRepository.existsByQuizIdAndUserId(1L, 2L)).willReturn(false);
    given(userRepository.findById(2L)).willReturn(Optional.of(viewer));

    QuizShareResponse response = quizShareService.recordShare(1L, 2L, null);

    assertThat(response.shareCount()).isEqualTo(6);
    assertThat(response.alreadyShared()).isFalse();
    assertThat(quiz.getShareCount()).isEqualTo(6);

    ArgumentCaptor<QuizShare> captor = ArgumentCaptor.forClass(QuizShare.class);
    then(quizShareRepository).should().saveAndFlush(captor.capture());
    QuizShare saved = captor.getValue();
    assertThat(saved.getQuiz()).isSameAs(quiz);
    assertThat(saved.getUser()).isSameAs(viewer);
    assertThat(saved.getAnonId()).isNull();
  }

  @Test
  @DisplayName("recordShare_로그인사용자_이미공유_save미호출_카운트변동없음_alreadyShared=true")
  void recordShare_loginUser_alreadyShared() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(quizShareRepository.existsByQuizIdAndUserId(1L, 2L)).willReturn(true);

    QuizShareResponse response = quizShareService.recordShare(1L, 2L, null);

    assertThat(response.shareCount()).isEqualTo(5);
    assertThat(response.alreadyShared()).isTrue();
    assertThat(quiz.getShareCount()).isEqualTo(5);
    then(quizShareRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("recordShare_비로그인_신규공유_anonId설정+user=null로저장+카운트증가")
  void recordShare_anonymous_firstShare() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    String anonId = "anon-uuid-xxx";
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(quizShareRepository.existsByQuizIdAndAnonId(1L, anonId)).willReturn(false);

    QuizShareResponse response = quizShareService.recordShare(1L, null, anonId);

    assertThat(response.shareCount()).isEqualTo(6);
    assertThat(response.alreadyShared()).isFalse();
    assertThat(quiz.getShareCount()).isEqualTo(6);

    ArgumentCaptor<QuizShare> captor = ArgumentCaptor.forClass(QuizShare.class);
    then(quizShareRepository).should().saveAndFlush(captor.capture());
    QuizShare saved = captor.getValue();
    assertThat(saved.getQuiz()).isSameAs(quiz);
    assertThat(saved.getUser()).isNull();
    assertThat(saved.getAnonId()).isEqualTo(anonId);
    then(userRepository).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("recordShare_비로그인_이미공유_save미호출_alreadyShared=true")
  void recordShare_anonymous_alreadyShared() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    String anonId = "anon-uuid-xxx";
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(quizShareRepository.existsByQuizIdAndAnonId(1L, anonId)).willReturn(true);

    QuizShareResponse response = quizShareService.recordShare(1L, null, anonId);

    assertThat(response.shareCount()).isEqualTo(5);
    assertThat(response.alreadyShared()).isTrue();
    assertThat(quiz.getShareCount()).isEqualTo(5);
    then(quizShareRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("recordShare_userId와anonId모두null_INVALID_INPUT")
  void recordShare_bothIdentifiersNull_throwsInvalidInput() {
    assertThatThrownBy(() -> quizShareService.recordShare(1L, null, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_INPUT);

    then(quizRepository).shouldHaveNoInteractions();
    then(quizShareRepository).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("recordShare_PRIVATE퀴즈_외부유저_QUIZ_NOT_FOUND")
  void recordShare_privateQuiz_externalUser_throws() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PRIVATE);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));

    assertThatThrownBy(() -> quizShareService.recordShare(1L, 2L, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);

    then(quizShareRepository).should(never()).saveAndFlush(any());
    assertThat(quiz.getShareCount()).isEqualTo(5);
  }

  @Test
  @DisplayName("recordShare_PRIVATE퀴즈_본인_정상신규공유")
  void recordShare_privateQuiz_owner_succeeds() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PRIVATE);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(quizShareRepository.existsByQuizIdAndUserId(1L, 1L)).willReturn(false);
    given(userRepository.findById(1L)).willReturn(Optional.of(owner));

    QuizShareResponse response = quizShareService.recordShare(1L, 1L, null);

    assertThat(response.shareCount()).isEqualTo(6);
    assertThat(response.alreadyShared()).isFalse();
    assertThat(quiz.getShareCount()).isEqualTo(6);
    then(quizShareRepository).should().saveAndFlush(any());
  }

  @Test
  @DisplayName("recordShare_quizId미존재_QUIZ_NOT_FOUND")
  void recordShare_quizNotFound_throws() {
    given(quizRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> quizShareService.recordShare(99L, 2L, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);

    then(quizShareRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("recordShare_saveAndFlush가DataIntegrityViolation_alreadyShared=true_카운트변동없음")
  void recordShare_dataIntegrityViolation_returnsAlreadyShared() {
    User owner = testUser(1L);
    User viewer = testUser(2L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(quizShareRepository.existsByQuizIdAndUserId(1L, 2L)).willReturn(false);
    given(userRepository.findById(2L)).willReturn(Optional.of(viewer));
    willThrow(new DataIntegrityViolationException("duplicate"))
        .given(quizShareRepository)
        .saveAndFlush(any(QuizShare.class));

    QuizShareResponse response = quizShareService.recordShare(1L, 2L, null);

    assertThat(response.shareCount()).isEqualTo(5);
    assertThat(response.alreadyShared()).isTrue();
    assertThat(quiz.getShareCount()).isEqualTo(5);
  }

  @Test
  @DisplayName("recordShare_로그인사용자_userRepository_empty_user=null로저장됨")
  void recordShare_loginUser_userMissing_savesWithNullUser() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizRepository.findById(1L)).willReturn(Optional.of(quiz));
    given(quizShareRepository.existsByQuizIdAndUserId(1L, 2L)).willReturn(false);
    given(userRepository.findById(2L)).willReturn(Optional.empty());

    QuizShareResponse response = quizShareService.recordShare(1L, 2L, null);

    assertThat(response.shareCount()).isEqualTo(6);
    assertThat(response.alreadyShared()).isFalse();

    ArgumentCaptor<QuizShare> captor = ArgumentCaptor.forClass(QuizShare.class);
    then(quizShareRepository).should().saveAndFlush(captor.capture());
    QuizShare saved = captor.getValue();
    assertThat(saved.getUser()).isNull();
    assertThat(saved.getAnonId()).isNull();
  }
}
