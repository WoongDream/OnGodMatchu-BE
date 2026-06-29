package com.ongodmatchu.domain.quiz.repository;

import com.ongodmatchu.domain.quiz.entity.Quiz;
import com.ongodmatchu.domain.quiz.entity.QuizStar;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface QuizStarRepository extends JpaRepository<QuizStar, Long> {

  boolean existsByUserIdAndQuizId(Long userId, Long quizId);

  long deleteByUserIdAndQuizId(Long userId, Long quizId);

  @Transactional
  void deleteByQuizIdIn(Collection<Long> quizIds);

  @Query("SELECT s.quiz.id FROM QuizStar s WHERE s.user.id = :userId AND s.quiz.id IN :quizIds")
  List<Long> findStarredQuizIds(
      @Param("userId") Long userId, @Param("quizIds") Collection<Long> quizIds);

  /**
   * 내가 스타 준 퀴즈 목록 — 스타 누른 시각(QuizStar.createdAt) DESC. 본인 조회라 PUBLIC/PRIVATE 모두 포함. title 이 주어지면 퀴즈
   * 제목 부분일치 필터(대소문자 무시). 작성자 닉네임 N+1 회피 위해 quiz.user fetch join. 정렬은 쿼리에 박혀 있으므로 호출자는 정렬 없는
   * Pageable 을 넘긴다.
   */
  @Query(
      value =
          "SELECT q FROM QuizStar s JOIN s.quiz q JOIN FETCH q.user "
              + "WHERE s.user.id = :userId "
              + "AND (CAST(:title AS string) IS NULL OR LOWER(q.title) LIKE LOWER(CONCAT('%', CAST(:title AS string), '%'))) "
              + "ORDER BY s.createdAt DESC",
      countQuery =
          "SELECT COUNT(s) FROM QuizStar s "
              + "WHERE s.user.id = :userId "
              + "AND (CAST(:title AS string) IS NULL OR LOWER(s.quiz.title) LIKE LOWER(CONCAT('%', CAST(:title AS string), '%')))")
  Page<Quiz> findStarredQuizzesByUserId(
      @Param("userId") Long userId, @Param("title") String title, Pageable pageable);
}
