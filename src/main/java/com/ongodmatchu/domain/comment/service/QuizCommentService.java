package com.ongodmatchu.domain.comment.service;

import com.ongodmatchu.domain.comment.dto.CommentCreateRequest;
import com.ongodmatchu.domain.comment.dto.CommentResponse;
import com.ongodmatchu.domain.comment.dto.CommentUpdateRequest;
import com.ongodmatchu.domain.comment.entity.QuizComment;
import com.ongodmatchu.domain.comment.repository.QuizCommentRepository;
import com.ongodmatchu.domain.comment.validation.CommentPolicy;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.s3.S3Service;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuizCommentService {

  private final QuizCommentRepository commentRepository;
  private final QuizRepository quizRepository;
  private final UserRepository userRepository;
  private final CommentPolicy commentPolicy;
  private final S3Service s3Service;

  @Transactional
  public CommentResponse create(Long userId, Long quizId, CommentCreateRequest request) {
    String content = commentPolicy.normalize(request.content());
    commentPolicy.enforce(content);

    Quiz quiz = findVisibleQuiz(userId, quizId);
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    QuizComment comment =
        commentRepository.save(
            QuizComment.builder().quiz(quiz).user(user).content(content).build());
    quiz.incrementCommentCount();
    return CommentResponse.from(comment, profileImageUrl(user));
  }

  @Transactional(readOnly = true)
  public Page<CommentResponse> list(Long viewerUserId, Long quizId, Pageable pageable) {
    findVisibleQuiz(viewerUserId, quizId);
    Page<QuizComment> page =
        commentRepository.findByQuizIdAndDeletedAtIsNullOrderByCreatedAtDesc(quizId, pageable);

    List<String> keys =
        page.getContent().stream()
            .map(c -> c.getUser().getProfileImageKey())
            .filter(k -> k != null)
            .toList();
    Map<String, String> presigned = s3Service.batchPresignViewUrls(keys);

    return page.map(
        c -> {
          String key = c.getUser().getProfileImageKey();
          return CommentResponse.from(c, key == null ? null : presigned.get(key));
        });
  }

  @Transactional
  public CommentResponse update(Long userId, Long commentId, CommentUpdateRequest request) {
    String content = commentPolicy.normalize(request.content());
    commentPolicy.enforce(content);

    QuizComment comment = findOwnedComment(userId, commentId);
    comment.updateContent(content);
    return CommentResponse.from(comment, profileImageUrl(comment.getUser()));
  }

  @Transactional
  public void delete(Long userId, Long commentId) {
    QuizComment comment = findOwnedComment(userId, commentId);
    if (comment.isDeleted()) {
      return;
    }
    comment.softDelete();
    comment.getQuiz().decrementCommentCount();
  }

  private QuizComment findOwnedComment(Long userId, Long commentId) {
    QuizComment comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
    if (comment.isDeleted()) {
      throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
    }
    if (!comment.getUser().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.COMMENT_FORBIDDEN);
    }
    return comment;
  }

  private Quiz findVisibleQuiz(Long viewerUserId, Long quizId) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
    boolean isOwner = viewerUserId != null && viewerUserId.equals(quiz.getUser().getId());
    if (quiz.getVisibility() == QuizVisibility.PRIVATE && !isOwner) {
      throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND);
    }
    return quiz;
  }

  private String profileImageUrl(User user) {
    String key = user.getProfileImageKey();
    return key == null ? null : s3Service.generateViewUrl(key).viewUrl();
  }
}
