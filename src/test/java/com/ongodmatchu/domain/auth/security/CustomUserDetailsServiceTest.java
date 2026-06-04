package com.ongodmatchu.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

  @InjectMocks private CustomUserDetailsService customUserDetailsService;
  @Mock private UserRepository userRepository;

  private User buildUser(boolean active) {
    return buildUser(active, false);
  }

  private User buildUser(boolean active, boolean system) {
    User user =
        User.builder()
            .email("user@example.com")
            .nickname("유저")
            .password("hashed")
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    ReflectionTestUtils.setField(user, "publicId", UUID.randomUUID());
    ReflectionTestUtils.setField(user, "isActive", active);
    ReflectionTestUtils.setField(user, "isSystem", system);
    return user;
  }

  @Test
  @DisplayName("loadUserByUsername_활성유저_정상반환")
  void loadUserByUsername_activeUser_returnsUserDetails() {
    User user = buildUser(true);
    given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));

    UserDetails result = customUserDetailsService.loadUserByUsername("user@example.com");

    assertThat(result).isInstanceOf(CustomUserDetails.class);
  }

  @Test
  @DisplayName("loadUserByUsername_탈퇴유저_UNAUTHORIZED예외")
  void loadUserByUsername_inactiveUser_throwsUnauthorized() {
    User user = buildUser(false);
    given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));

    assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("user@example.com"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  @DisplayName("loadUserById_활성유저_정상반환")
  void loadUserById_activeUser_returnsUserDetails() {
    User user = buildUser(true);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    UserDetails result = customUserDetailsService.loadUserById(1L);

    assertThat(result).isInstanceOf(CustomUserDetails.class);
  }

  @Test
  @DisplayName("loadUserById_탈퇴유저_UNAUTHORIZED예외")
  void loadUserById_inactiveUser_throwsUnauthorized() {
    User user = buildUser(false);
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    assertThatThrownBy(() -> customUserDetailsService.loadUserById(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  @DisplayName("loadUserByUsername_사용자없음_USER_NOT_FOUND예외")
  void loadUserByUsername_notFound_throwsException() {
    given(userRepository.findByEmail("none@example.com")).willReturn(Optional.empty());

    assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("none@example.com"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("loadUserByUsername_시스템계정_정상반환(OWNER 승격)")
  void loadUserByUsername_systemAccount_returnsUserDetails() {
    User systemUser = buildUser(true, true);
    given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(systemUser));

    UserDetails result = customUserDetailsService.loadUserByUsername("user@example.com");

    assertThat(result).isInstanceOf(CustomUserDetails.class);
  }

  @Test
  @DisplayName("loadUserById_시스템계정_정상반환(OWNER 승격)")
  void loadUserById_systemAccount_returnsUserDetails() {
    User systemUser = buildUser(true, true);
    given(userRepository.findById(1L)).willReturn(Optional.of(systemUser));

    UserDetails result = customUserDetailsService.loadUserById(1L);

    assertThat(result).isInstanceOf(CustomUserDetails.class);
  }
}
