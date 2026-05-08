package com.ongodmatchu.domain.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserDisplayTest {

  private User buildUser(boolean active) {
    User user =
        User.builder()
            .email("user@example.com")
            .nickname("홍길동")
            .password("hashed")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    ReflectionTestUtils.setField(user, "publicId", UUID.randomUUID());
    ReflectionTestUtils.setField(user, "isActive", active);
    return user;
  }

  @Test
  @DisplayName("nicknameOf_활성유저_원래닉네임반환")
  void nicknameOf_active_returnsOriginal() {
    assertThat(UserDisplay.nicknameOf(buildUser(true))).isEqualTo("홍길동");
  }

  @Test
  @DisplayName("nicknameOf_탈퇴유저_탈퇴한사용자반환")
  void nicknameOf_inactive_returnsWithdrawnNickname() {
    assertThat(UserDisplay.nicknameOf(buildUser(false))).isEqualTo("탈퇴한 사용자");
  }

  @Test
  @DisplayName("nicknameOf_null_탈퇴한사용자반환")
  void nicknameOf_null_returnsWithdrawnNickname() {
    assertThat(UserDisplay.nicknameOf(null)).isEqualTo("탈퇴한 사용자");
  }

  @Test
  @DisplayName("profileImageUrlOf_활성유저_resolvedUrl그대로")
  void profileImageUrlOf_active_returnsResolvedUrl() {
    assertThat(UserDisplay.profileImageUrlOf(buildUser(true), "https://x/y.jpg"))
        .isEqualTo("https://x/y.jpg");
  }

  @Test
  @DisplayName("profileImageUrlOf_탈퇴유저_null반환")
  void profileImageUrlOf_inactive_returnsNull() {
    assertThat(UserDisplay.profileImageUrlOf(buildUser(false), "https://x/y.jpg")).isNull();
  }
}
