package com.ongodmatchu.domain.comment.repository;

import com.ongodmatchu.domain.comment.entity.QuizComment;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface QuizCommentRepository extends JpaRepository<QuizComment, Long> {

  Page<QuizComment> findByQuizIdAndDeletedAtIsNullOrderByCreatedAtDesc(
      Long quizId, Pageable pageable);

  @Transactional
  void deleteByQuizIdIn(Collection<Long> quizIds);
}
