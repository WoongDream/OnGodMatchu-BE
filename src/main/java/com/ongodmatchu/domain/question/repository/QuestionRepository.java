package com.ongodmatchu.domain.question.repository;

import com.ongodmatchu.domain.question.entity.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

  List<Question> findByQuizIdOrderByOrderNum(Long quizId);
}
