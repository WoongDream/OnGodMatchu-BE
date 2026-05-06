package com.ongodmatchu.domain.user.repository;

import com.ongodmatchu.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  Optional<User> findByPublicId(UUID publicId);

  boolean existsByEmail(String email);

  boolean existsByNickname(String nickname);
}
