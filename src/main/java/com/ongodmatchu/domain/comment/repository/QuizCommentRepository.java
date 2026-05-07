package com.ongodmatchu.domain.comment.repository;

import com.ongodmatchu.domain.comment.entity.QuizComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizCommentRepository extends JpaRepository<QuizComment, Long> {

  Page<QuizComment> findByQuizIdAndDeletedAtIsNullOrderByCreatedAtDesc(
      Long quizId, Pageable pageable);
}
