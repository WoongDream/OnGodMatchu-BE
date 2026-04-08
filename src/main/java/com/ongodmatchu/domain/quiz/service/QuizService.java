package com.ongodmatchu.domain.quiz.service;

import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.dto.QuestionResponse;
import com.ongodmatchu.domain.quiz.dto.QuizCreateRequest;
import com.ongodmatchu.domain.quiz.dto.QuizDetailResponse;
import com.ongodmatchu.domain.quiz.dto.QuizResponse;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QuizService {

  private final QuizRepository quizRepository;
  private final QuestionRepository questionRepository;
  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public Page<QuizResponse> getQuizList(String category, Pageable pageable) {
    if (StringUtils.hasText(category)) {
      return quizRepository.findByCategory(category, pageable).map(QuizResponse::from);
    }
    return quizRepository.findAll(pageable).map(QuizResponse::from);
  }

  @Transactional(readOnly = true)
  public QuizDetailResponse getQuizDetail(Long quizId) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
    List<QuestionResponse> questions =
        questionRepository.findByQuizIdOrderByOrderNum(quizId).stream()
            .map(QuestionResponse::from)
            .toList();
    return QuizDetailResponse.of(quiz, questions);
  }

  @Transactional
  public QuizResponse createQuiz(Long userId, QuizCreateRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    Quiz quiz =
        Quiz.builder()
            .user(user)
            .title(request.title())
            .description(request.description())
            .category(request.category())
            .thumbnailUrl(request.thumbnailUrl())
            .build();
    quizRepository.save(quiz);

    for (int i = 0; i < request.questions().size(); i++) {
      var q = request.questions().get(i);
      questionRepository.save(
          Question.builder()
              .quiz(quiz)
              .orderNum(i + 1)
              .imageUrl(q.imageUrl())
              .questionText(q.questionText())
              .answer(q.answer())
              .build());
    }

    return QuizResponse.from(quiz);
  }

  @Transactional
  public void incrementPlayCount(Long quizId) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
    quiz.incrementPlayCount();
  }
}
