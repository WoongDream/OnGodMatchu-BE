package com.ongodmatchu.domain.auth.repository;

import com.ongodmatchu.domain.auth.entity.EmailVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

  Optional<EmailVerification> findTopByEmailOrderByCreatedAtDesc(String email);

  void deleteByEmail(String email);
}
