package com.ongodmatchu.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("이메일로 유저를 조회한다")
  void findByEmail() {
    User user =
        User.builder()
            .email("test@example.com")
            .nickname("RepoTest_findByEmail")
            .password("hashed_password")
            .provider(AuthProvider.LOCAL)
            .emailVerified(false)
            .build();
    userRepository.save(user);

    Optional<User> found = userRepository.findByEmail("test@example.com");

    assertThat(found).isPresent();
    assertThat(found.get().getNickname()).isEqualTo("RepoTest_findByEmail");
  }

  @Test
  @DisplayName("이메일 중복 여부를 확인한다")
  void existsByEmail() {
    User user =
        User.builder()
            .email("dup@example.com")
            .nickname("RepoTest_existsByEmail")
            .provider(AuthProvider.LOCAL)
            .emailVerified(false)
            .build();
    userRepository.save(user);

    assertThat(userRepository.existsByEmail("dup@example.com")).isTrue();
    assertThat(userRepository.existsByEmail("none@example.com")).isFalse();
  }

  @Test
  @DisplayName("이메일 인증 후 emailVerified 가 true 로 변경된다")
  void verifyEmail() {
    User user =
        User.builder()
            .email("verify@example.com")
            .nickname("RepoTest_verifyEmail")
            .provider(AuthProvider.LOCAL)
            .emailVerified(false)
            .build();
    userRepository.save(user);

    user.verifyEmail();
    userRepository.save(user);

    User found = userRepository.findByEmail("verify@example.com").orElseThrow();
    assertThat(found.isEmailVerified()).isTrue();
  }
}
