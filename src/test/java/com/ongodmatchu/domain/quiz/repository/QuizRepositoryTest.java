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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

  // ============ searchPublic — visibility 한정 + category·title 옵션 + Pageable 정렬 ============

  /**
   * searchPublic 은 userId 한정 없이 PUBLIC 퀴즈 전역을 조회하므로, 테스트 DB(실제 Postgres)에 사전 존재하는 행이 결과에 섞여 카운트·정렬
   * 검증이 비결정적이 된다. @DataJpaTest 의 트랜잭션 롤백 안에서 quiz 와 그 자식 행들을 FK 순서로 비운 뒤 픽스처를 세팅해 격리한다. (테스트 종료 시
   * 롤백되어 원본 DB 는 보존됨.)
   */
  private void clearAllQuizzes() {
    em.flush();
    em.createNativeQuery("DELETE FROM quiz_attempts").executeUpdate();
    em.createNativeQuery("DELETE FROM quiz_stars").executeUpdate();
    em.createNativeQuery("DELETE FROM quiz_comments").executeUpdate();
    em.createNativeQuery("DELETE FROM quiz_share").executeUpdate();
    em.createNativeQuery("DELETE FROM questions").executeUpdate();
    em.createNativeQuery("DELETE FROM quizzes").executeUpdate();
    em.clear();
  }

  /**
   * category·title·visibility·playCount 를 지정해 PUBLIC 목록 검색용 픽스처를 만든다. playCount 는
   * incrementPlayCount() 반복으로 채운다.
   */
  private Quiz savedQuizFor(
      User owner, String title, String category, QuizVisibility visibility, int playCount) {
    Quiz quiz = Quiz.builder().user(owner).title(title).category(category).build();
    quiz.changeVisibility(visibility);
    for (int i = 0; i < playCount; i++) {
      quiz.incrementPlayCount();
    }
    return quizRepository.save(quiz);
  }

  /**
   * created_at 을 명시적으로 지정한다. @CreatedDate auditing 은 동일 트랜잭션 내 INSERT 들에 동일 시각을 부여할 수 있어 createdAt
   * 기준 정렬 검증이 비결정적이 된다. created_at 은 updatable=false 라 dirty checking 으로는 갱신되지 않으므로 native UPDATE 로
   * DB 값을 직접 박아 순서를 확정한다.
   */
  private void setCreatedAt(Quiz quiz, LocalDateTime createdAt) {
    em.flush();
    em.createNativeQuery("UPDATE quizzes SET created_at = :ts WHERE id = :id")
        .setParameter("ts", createdAt)
        .setParameter("id", quiz.getId())
        .executeUpdate();
  }

  @Test
  @DisplayName("searchPublic_PUBLIC만_반환하고_PRIVATE은_제외한다")
  void searchPublic_returnsOnlyPublic() {
    User user = savedUser();
    clearAllQuizzes();
    savedQuizFor(user, "공개 퀴즈", "general", QuizVisibility.PUBLIC, 0);
    savedQuizFor(user, "비공개 퀴즈", "general", QuizVisibility.PRIVATE, 0);
    em.flush();
    em.clear();

    Page<Quiz> page =
        quizRepository.searchPublic(QuizVisibility.PUBLIC, null, null, PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent()).extracting(Quiz::getTitle).containsExactly("공개 퀴즈");
  }

  @Test
  @DisplayName("searchPublic_category지정시_해당카테고리만_null이면_전체")
  void searchPublic_categoryFilter() {
    User user = savedUser();
    clearAllQuizzes();
    savedQuizFor(user, "게임 퀴즈", "game", QuizVisibility.PUBLIC, 0);
    savedQuizFor(user, "음악 퀴즈", "music", QuizVisibility.PUBLIC, 0);
    em.flush();
    em.clear();

    Page<Quiz> game =
        quizRepository.searchPublic(QuizVisibility.PUBLIC, "game", null, PageRequest.of(0, 10));
    assertThat(game.getTotalElements()).isEqualTo(1L);
    assertThat(game.getContent()).extracting(Quiz::getTitle).containsExactly("게임 퀴즈");

    // category=null → CAST(:category AS string) IS NULL 가드로 전체 반환
    Page<Quiz> all =
        quizRepository.searchPublic(QuizVisibility.PUBLIC, null, null, PageRequest.of(0, 10));
    assertThat(all.getTotalElements()).isEqualTo(2L);
  }

  @Test
  @DisplayName("searchPublic_title_대소문자무시_부분일치_null이면_전체")
  void searchPublic_titleFilterCaseInsensitive() {
    User user = savedUser();
    clearAllQuizzes();
    savedQuizFor(user, "Super Mario", "game", QuizVisibility.PUBLIC, 0);
    savedQuizFor(user, "Zelda", "game", QuizVisibility.PUBLIC, 0);
    em.flush();
    em.clear();

    // 소문자 "mario" 가 "Super Mario" 에 매칭돼야 함
    Page<Quiz> matched =
        quizRepository.searchPublic(QuizVisibility.PUBLIC, null, "mario", PageRequest.of(0, 10));
    assertThat(matched.getTotalElements()).isEqualTo(1L);
    assertThat(matched.getContent()).extracting(Quiz::getTitle).containsExactly("Super Mario");

    // title=null → CAST(:title AS string) IS NULL 가드로 전체 반환
    Page<Quiz> all =
        quizRepository.searchPublic(QuizVisibility.PUBLIC, null, null, PageRequest.of(0, 10));
    assertThat(all.getTotalElements()).isEqualTo(2L);
  }

  @Test
  @DisplayName("searchPublic_category와_title_AND_동시적용")
  void searchPublic_categoryAndTitleAnd() {
    User user = savedUser();
    clearAllQuizzes();
    // game + "mario" 둘 다 만족
    savedQuizFor(user, "Super Mario", "game", QuizVisibility.PUBLIC, 0);
    // title 은 만족하지만 category 불일치
    savedQuizFor(user, "Mario Song", "music", QuizVisibility.PUBLIC, 0);
    // category 는 만족하지만 title 불일치
    savedQuizFor(user, "Zelda", "game", QuizVisibility.PUBLIC, 0);
    em.flush();
    em.clear();

    Page<Quiz> page =
        quizRepository.searchPublic(QuizVisibility.PUBLIC, "game", "mario", PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent()).extracting(Quiz::getTitle).containsExactly("Super Mario");
  }

  @Test
  @DisplayName("searchPublic_정렬_playCount우선_createdAt_tiebreaker_DESC")
  void searchPublic_sortByPlayCountThenCreatedAt() {
    User user = savedUser();
    clearAllQuizzes();
    // playCount: high(10) > tie 두 건(5) > low(1)
    Quiz high = savedQuizFor(user, "인기최고", "general", QuizVisibility.PUBLIC, 10);
    Quiz tieOld = savedQuizFor(user, "동점_오래됨", "general", QuizVisibility.PUBLIC, 5);
    Quiz tieNew = savedQuizFor(user, "동점_최신", "general", QuizVisibility.PUBLIC, 5);
    Quiz low = savedQuizFor(user, "인기최저", "general", QuizVisibility.PUBLIC, 1);

    LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
    setCreatedAt(high, base.plusMinutes(1));
    // 동점 두 건은 createdAt tiebreaker(DESC) 로 tieNew 가 tieOld 보다 먼저
    setCreatedAt(tieOld, base.plusMinutes(2));
    setCreatedAt(tieNew, base.plusMinutes(3));
    setCreatedAt(low, base.plusMinutes(4));
    em.flush();
    em.clear();

    Page<Quiz> page =
        quizRepository.searchPublic(
            QuizVisibility.PUBLIC,
            null,
            null,
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "playCount", "createdAt")));

    assertThat(page.getContent())
        .extracting(Quiz::getTitle)
        .containsExactly("인기최고", "동점_최신", "동점_오래됨", "인기최저");
  }

  @Test
  @DisplayName("searchPublic_정렬_createdAt_DESC_최신순")
  void searchPublic_sortByCreatedAtDesc() {
    User user = savedUser();
    clearAllQuizzes();
    Quiz first = savedQuizFor(user, "첫번째", "general", QuizVisibility.PUBLIC, 0);
    Quiz second = savedQuizFor(user, "두번째", "general", QuizVisibility.PUBLIC, 0);
    Quiz third = savedQuizFor(user, "세번째", "general", QuizVisibility.PUBLIC, 0);

    LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
    setCreatedAt(first, base.plusMinutes(1));
    setCreatedAt(second, base.plusMinutes(2));
    setCreatedAt(third, base.plusMinutes(3));
    em.flush();
    em.clear();

    Page<Quiz> page =
        quizRepository.searchPublic(
            QuizVisibility.PUBLIC,
            null,
            null,
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

    assertThat(page.getContent()).extracting(Quiz::getTitle).containsExactly("세번째", "두번째", "첫번째");
  }

  @Test
  @DisplayName("searchPublic_매칭없음_빈페이지")
  void searchPublic_noMatch_returnsEmptyPage() {
    User user = savedUser();
    clearAllQuizzes();
    savedQuizFor(user, "Super Mario", "game", QuizVisibility.PUBLIC, 0);
    em.flush();
    em.clear();

    Page<Quiz> page =
        quizRepository.searchPublic(
            QuizVisibility.PUBLIC, "nonexistent", null, PageRequest.of(0, 10));

    assertThat(page.getContent()).isEmpty();
    assertThat(page.getTotalElements()).isZero();
  }
}
