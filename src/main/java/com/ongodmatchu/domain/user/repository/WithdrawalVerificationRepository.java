package com.ongodmatchu.domain.user.repository;

import com.ongodmatchu.domain.user.entity.WithdrawalVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface WithdrawalVerificationRepository
    extends JpaRepository<WithdrawalVerification, Long> {

  Optional<WithdrawalVerification> findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(
      Long userId);

  @Transactional
  void deleteByUserId(Long userId);
}
