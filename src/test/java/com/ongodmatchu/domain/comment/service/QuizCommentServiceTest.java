package com.ongodmatchu.domain.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.comment.dto.CommentCreateRequest;
import com.ongodmatchu.domain.comment.dto.CommentResponse;
import com.ongodmatchu.domain.comment.dto.CommentUpdateRequest;
import com.ongodmatchu.domain.comment.entity.QuizComment;
import com.ongodmatchu.domain.comment.repository.QuizCommentRepository;
import com.ongodmatchu.domain.comment.validation.CommentPolicy;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.s3.S3Service;
import com.ongodmatchu.infra.s3.ViewUrlResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuizCommentServiceTest {

  @InjectMocks private QuizCommentService commentService;
  @Mock private QuizCommentRepository commentRepository;
  @Mock private QuizRepository quizRepository;
  @Mock private UserRepository userRepository;
  @Mock private S3Service s3Service;

  // CommentPolicy 는 stateless component — spy 대신 직접 주입
  private final CommentPolicy commentPolicy = new CommentPolicy();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(commentService, "commentPolicy", commentPolicy);
    lenient().when(s3Service.batchPresignViewUrls(any())).thenReturn(Map.of());
    lenient()
        .when(s3Service.generateViewUrl(anyString()))
        .thenAnswer(
            inv ->
                new ViewUrlResponse(
                    "https://signed/" + inv.getArgument(0),
                    inv.getArgument(0),
                    3600L,
                    Instant.now().plusSeconds(3600)));
  }

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

  private QuizComment savedComment(Quiz quiz, User user, String content) {
    QuizComment comment = QuizComment.builder().quiz(quiz).user(user).content(content).build();
    ReflectionTestUtils.setField(comment, "id", 100L);
    return comment;
  }

  @Test
  @DisplayName("create_PUBLIC퀴즈_정상_저장+카운터증가")
  void create_publicQuiz_succeeds() {
    User owner = testUser(1L);
    User viewer = testUser(2L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    given(quizRepository.findById(10L)).willReturn(Optional.of(quiz));
    given(userRepository.findById(2L)).willReturn(Optional.of(viewer));
    given(commentRepository.save(any())).willAnswer(inv -> inv.<QuizComment>getArgument(0));

    CommentResponse response = commentService.create(2L, 10L, new CommentCreateRequest("좋은 퀴즈네요"));

    assertThat(response.content()).isEqualTo("좋은 퀴즈네요");
    assertThat(quiz.getCommentCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("create_PRIVATE퀴즈_외부유저_QUIZ_NOT_FOUND")
  void create_privateQuiz_externalUser_throws() {
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PRIVATE);
    given(quizRepository.findById(10L)).willReturn(Optional.of(quiz));

    assertThatThrownBy(() -> commentService.create(2L, 10L, new CommentCreateRequest("내용")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUIZ_NOT_FOUND);
    then(commentRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("create_빈내용_INVALID_COMMENT_FORMAT")
  void create_blankContent_throws() {
    assertThatThrownBy(() -> commentService.create(2L, 10L, new CommentCreateRequest("   ")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_COMMENT_FORMAT);
  }

  @Test
  @DisplayName("update_본인댓글_내용변경")
  void update_owner_succeeds() {
    User author = testUser(2L);
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    QuizComment comment = savedComment(quiz, author, "원본");
    given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

    CommentResponse response = commentService.update(2L, 100L, new CommentUpdateRequest("수정됨"));

    assertThat(response.content()).isEqualTo("수정됨");
    assertThat(comment.getContent()).isEqualTo("수정됨");
  }

  @Test
  @DisplayName("update_타인댓글_COMMENT_FORBIDDEN")
  void update_notOwner_throws() {
    User author = testUser(2L);
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    QuizComment comment = savedComment(quiz, author, "원본");
    given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

    assertThatThrownBy(() -> commentService.update(99L, 100L, new CommentUpdateRequest("내가 수정")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.COMMENT_FORBIDDEN);
  }

  @Test
  @DisplayName("delete_본인댓글_softDelete+카운터감소")
  void delete_owner_softDeletes() {
    User author = testUser(2L);
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    quiz.incrementCommentCount();
    QuizComment comment = savedComment(quiz, author, "내용");
    given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

    commentService.delete(2L, 100L);

    assertThat(comment.isDeleted()).isTrue();
    assertThat(quiz.getCommentCount()).isZero();
  }

  @Test
  @DisplayName("delete_타인댓글_COMMENT_FORBIDDEN")
  void delete_notOwner_throws() {
    User author = testUser(2L);
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    QuizComment comment = savedComment(quiz, author, "내용");
    given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

    assertThatThrownBy(() -> commentService.delete(99L, 100L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.COMMENT_FORBIDDEN);
  }

  @Test
  @DisplayName("update_삭제된댓글_COMMENT_NOT_FOUND")
  void update_deletedComment_throws() {
    User author = testUser(2L);
    User owner = testUser(1L);
    Quiz quiz = testQuiz(owner, QuizVisibility.PUBLIC);
    QuizComment comment = savedComment(quiz, author, "내용");
    comment.softDelete();
    given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

    assertThatThrownBy(() -> commentService.update(2L, 100L, new CommentUpdateRequest("수정")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
  }
}
