package com.ongodmatchu.domain.quiz.service;

import com.ongodmatchu.domain.question.entity.Question;
import com.ongodmatchu.domain.question.repository.QuestionRepository;
import com.ongodmatchu.domain.quiz.dto.AttemptAnswerRequest;
import com.ongodmatchu.domain.quiz.dto.AttemptCreateRequest;
import com.ongodmatchu.domain.quiz.dto.AttemptItemResultResponse;
import com.ongodmatchu.domain.quiz.dto.AttemptListItemResponse;
import com.ongodmatchu.domain.quiz.dto.AttemptResultResponse;
import com.ongodmatchu.domain.quiz.dto.ScoreCountResponse;
import com.ongodmatchu.domain.quiz.dto.ScoreDistributionResponse;
import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizAttempt;
import com.ongodmatchu.domain.quiz.entity.QuizVisibility;
import com.ongodmatchu.domain.quiz.repository.QuizAttemptRepository;
import com.ongodmatchu.domain.quiz.repository.QuizRepository;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import com.ongodmatchu.infra.ai.AiGradingService;
import com.ongodmatchu.infra.s3.S3Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuizAttemptService {

  private static final int MAX_PAGE_SIZE = 50;
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final QuizRepository quizRepository;
  private final QuestionRepository questionRepository;
  private final QuizAttemptRepository quizAttemptRepository;
  private final UserRepository userRepository;
  private final AiGradingService aiGradingService;
  private final S3Service s3Service;

  /**
   * 풀이 1회 제출 (A안 — 서버 일괄 채점). PRIVATE 퀴즈 + 외부 뷰어면 QUIZ_NOT_FOUND. attempt 는 비로그인 포함 항상 저장 (비로그인이면
   * user=null) → 퀴즈 작성자 기준 집계(weeklyPlayCount / correctRate)에 반영. "내 풀이 기록"(/me/attempts) 은 user_id
   * 로 필터하므로 비로그인 attempt 는 자연 제외.
   */
  @Transactional
  public AttemptResultResponse submit(
      Long quizId, Long viewerUserId, AttemptCreateRequest request) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));

    boolean isOwner = viewerUserId != null && viewerUserId.equals(quiz.getUser().getId());
    if (quiz.getVisibility() == QuizVisibility.PRIVATE && !isOwner) {
      throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND);
    }

    List<Question> questions = questionRepository.findByQuizIdOrderByOrderNum(quizId);
    Map<Long, Question> byId = new HashMap<>();
    for (Question q : questions) {
      byId.put(q.getId(), q);
    }

    List<String> answerImageKeys =
        questions.stream().map(Question::getAnswerImageKey).filter(k -> k != null).toList();
    Map<String, String> answerImageUrls = s3Service.batchPresignViewUrls(answerImageKeys);

    List<AttemptItemResultResponse> results = new ArrayList<>(request.answers().size());
    int score = 0;
    for (AttemptAnswerRequest a : request.answers()) {
      Question q = byId.get(a.questionId());
      if (q == null) {
        throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
      }
      // 시간 초과로 들어온 빈 답은 무조건 오답 (AI 호출 skip)
      boolean correct =
          !a.userAnswer().trim().isEmpty()
              && (isExactMatch(q.getAnswer(), a.userAnswer())
                  || aiGradingService.grade(q.getAnswer(), a.userAnswer()));
      if (correct) {
        score++;
      }
      results.add(
          new AttemptItemResultResponse(
              q.getId(),
              correct,
              q.getAnswer(),
              a.userAnswer(),
              lookupUrl(answerImageUrls, q.getAnswerImageKey())));
    }

    int totalQuestions = questions.size();
    quiz.incrementPlayCount();

    User user = null;
    if (viewerUserId != null) {
      user =
          userRepository
              .findById(viewerUserId)
              .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
    QuizAttempt saved =
        quizAttemptRepository.save(
            QuizAttempt.builder()
                .user(user)
                .quiz(quiz)
                .score(score)
                .totalQuestions(totalQuestions)
                .timeLimitSec(request.timeLimitSec())
                .build());
    Long attemptId = viewerUserId != null ? saved.getId() : null;

    Double percent = totalQuestions > 0 ? (score * 100.0 / totalQuestions) : null;
    // 방금 저장한 attempt 를 포함한 분포로 산출 후, 풀이 당시 스냅샷으로 함께 기록.
    Double topPercentile = computeTopPercentile(quizId, score);
    saved.assignTopPercentile(topPercentile);
    return new AttemptResultResponse(
        attemptId, score, totalQuestions, percent, topPercentile, results);
  }

  /**
   * 퀴즈 점수 분포 + 평균 + 응시자 수. PRIVATE 외부 → QUIZ_NOT_FOUND. distribution 은 score 0~totalQuestions 전 칸
   * 포함 (응시 없는 score 도 count=0).
   */
  @Transactional(readOnly = true)
  public ScoreDistributionResponse getScoreDistribution(Long quizId, Long viewerUserId) {
    Quiz quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND));
    boolean isOwner = viewerUserId != null && viewerUserId.equals(quiz.getUser().getId());
    if (quiz.getVisibility() == QuizVisibility.PRIVATE && !isOwner) {
      throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND);
    }

    int totalQuestions = (int) questionRepository.countByQuizId(quizId);
    List<QuizAttemptRepository.ScoreBucketRow> rows =
        quizAttemptRepository.findScoreDistributionByQuizId(quizId);

    Map<Integer, Long> bucket = new HashMap<>();
    long totalAttempts = 0L;
    long scoreSum = 0L;
    for (QuizAttemptRepository.ScoreBucketRow row : rows) {
      int s = row.getScore();
      long c = row.getCount();
      bucket.put(s, c);
      totalAttempts += c;
      scoreSum += (long) s * c;
    }

    List<ScoreCountResponse> distribution = new ArrayList<>(totalQuestions + 1);
    for (int i = 0; i <= totalQuestions; i++) {
      distribution.add(new ScoreCountResponse(i, bucket.getOrDefault(i, 0L)));
    }

    double averageScore =
        totalAttempts == 0 ? 0.0 : Math.round(scoreSum * 10.0 / totalAttempts) / 10.0;
    return new ScoreDistributionResponse(totalAttempts, averageScore, distribution);
  }

  /**
   * 본인 attempt 포함 + 동률 중간 처리 백분위 — (countGreaterThan + countEqual/2) / totalAttempts * 100. 본인이 첫
   * 응시자(totalAttempts=1)면 null.
   */
  private Double computeTopPercentile(Long quizId, int score) {
    long total = quizAttemptRepository.countByQuizId(quizId);
    if (total < 2) {
      return null;
    }
    long gt = quizAttemptRepository.countByQuizIdAndScoreGreaterThan(quizId, score);
    long eq = quizAttemptRepository.countByQuizIdAndScore(quizId, score);
    double raw = (gt + eq / 2.0) / total * 100.0;
    return Math.round(raw * 10.0) / 10.0;
  }

  /**
   * 내 풀이 기록 목록 — 퀴즈 단위로 그룹화하여 quiz 당 최신 기록 1건 + 풀이 횟수. 최신 풀이 시각 DESC, size 디폴트 20 / 최대 50. title 이
   * 주어지면 퀴즈 제목 부분일치 필터.
   */
  @Transactional(readOnly = true)
  public Page<AttemptListItemResponse> getMyAttempts(Long userId, String title, Pageable pageable) {
    return mapGroupsToListItems(userId, normalizeTitle(title), applyPageDefaults(pageable));
  }

  /** 외부 뷰어 + 비공개 프로필 → 빈 페이지. 본인이거나 공개 프로필이면 정상 반환. */
  @Transactional(readOnly = true)
  public Page<AttemptListItemResponse> getAttemptsByPublicId(
      UUID publicId, Long viewerUserId, Pageable pageable) {
    User author =
        userRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    Pageable effective = applyPageDefaults(pageable);
    boolean isOwner = viewerUserId != null && viewerUserId.equals(author.getId());
    if (!author.isProfilePublic() && !isOwner) {
      return Page.empty(effective);
    }
    return mapGroupsToListItems(author.getId(), null, effective);
  }

  /** quiz 단위 그룹 페이지를 받아 각 그룹의 최신 attempt + 누적 횟수 + 썸네일 presign 으로 응답 매핑 (그룹 정렬 순서 보존). */
  private Page<AttemptListItemResponse> mapGroupsToListItems(
      Long userId, String title, Pageable effective) {
    Page<QuizAttemptRepository.AttemptGroupRow> groups =
        quizAttemptRepository.findAttemptGroupsByUserId(userId, title, effective);
    if (groups.isEmpty()) {
      return Page.empty(effective);
    }

    List<Long> quizIds = groups.getContent().stream().map(g -> g.getQuizId()).toList();
    Map<Long, Long> attemptCountByQuizId = new HashMap<>();
    for (QuizAttemptRepository.AttemptGroupRow g : groups.getContent()) {
      attemptCountByQuizId.put(g.getQuizId(), g.getAttemptCount());
    }

    Map<Long, QuizAttempt> latestByQuizId = new HashMap<>();
    for (QuizAttempt a : quizAttemptRepository.findLatestAttemptsPerQuiz(userId, quizIds)) {
      latestByQuizId.put(a.getQuiz().getId(), a);
    }

    List<String> keys =
        latestByQuizId.values().stream()
            .map(a -> a.getQuiz().getThumbnailKey())
            .filter(k -> k != null)
            .toList();
    Map<String, String> presigned = s3Service.batchPresignViewUrls(keys);

    List<AttemptListItemResponse> items = new ArrayList<>(quizIds.size());
    for (Long quizId : quizIds) {
      QuizAttempt latest = latestByQuizId.get(quizId);
      if (latest == null) {
        continue; // 동시 삭제 등으로 사라진 경우 방어적 skip
      }
      items.add(
          AttemptListItemResponse.from(
              latest,
              lookupUrl(presigned, latest.getQuiz().getThumbnailKey()),
              attemptCountByQuizId.getOrDefault(quizId, 1L)));
    }
    return new PageImpl<>(items, effective, groups.getTotalElements());
  }

  private static String normalizeTitle(String title) {
    return (title == null || title.isBlank()) ? null : title.trim();
  }

  private static String lookupUrl(Map<String, String> presigned, String key) {
    return key == null ? null : presigned.get(key);
  }

  private boolean isExactMatch(String correctAnswer, String userAnswer) {
    return correctAnswer.trim().equalsIgnoreCase(userAnswer.trim());
  }

  private Pageable applyPageDefaults(Pageable pageable) {
    int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
    if (size <= 0) {
      size = DEFAULT_PAGE_SIZE;
    }
    return PageRequest.of(pageable.getPageNumber(), size);
  }
}
