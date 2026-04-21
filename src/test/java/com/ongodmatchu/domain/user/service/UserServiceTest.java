package com.ongodmatchu.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.ongodmatchu.domain.user.dto.UserResponse;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 테스트")
class UserServiceTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private UserService userService;

  @BeforeEach
  void setUp() {}

  private User createUserWithId(Long id, String email, String nickname, AuthProvider provider) {
    User user =
        User.builder()
            .email(email)
            .nickname(nickname)
            .password("hashedPassword123")
            .provider(provider)
            .emailVerified(true)
            .build();
    try {
      Field idField = User.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(user, id);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    return user;
  }

  @Test
  @DisplayName("정상: 유효한 userId로 사용자 조회 시 UserResponse 반환")
  void getMe_withValidUserId_returnsUserResponse() {
    // given
    Long userId = 1L;
    User testUser = createUserWithId(userId, "test@example.com", "테스트유저", AuthProvider.LOCAL);
    given(userRepository.findById(userId)).willReturn(Optional.of(testUser));

    // when
    UserResponse response = userService.getMe(userId);

    // then
    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo(testUser.getId());
    assertThat(response.email()).isEqualTo(testUser.getEmail());
    assertThat(response.nickname()).isEqualTo(testUser.getNickname());
    assertThat(response.provider()).isEqualTo(testUser.getProvider().name());
  }

  @Test
  @DisplayName("정상: 반환된 UserResponse에 모든 사용자 정보 포함")
  void getMe_withValidUserId_includesAllUserInfo() {
    // given
    Long userId = 1L;
    User userWithProvider =
        createUserWithId(userId, "google@example.com", "구글유저", AuthProvider.GOOGLE);
    given(userRepository.findById(userId)).willReturn(Optional.of(userWithProvider));

    // when
    UserResponse response = userService.getMe(userId);

    // then
    assertThat(response.id()).isEqualTo(userId);
    assertThat(response.email()).isEqualTo("google@example.com");
    assertThat(response.nickname()).isEqualTo("구글유저");
    assertThat(response.provider()).isEqualTo("GOOGLE");
  }

  @Test
  @DisplayName("에러: 존재하지 않는 userId로 조회 시 BusinessException 발생")
  void getMe_withNonExistentUserId_throwsBusinessException() {
    // given
    Long userId = 999L;
    given(userRepository.findById(userId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> userService.getMe(userId))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("경계값: userId = 1로 조회")
  void getMe_withUserIdOne_returnsUserResponse() {
    // given
    Long userId = 1L;
    User testUser = createUserWithId(userId, "test@example.com", "테스트유저", AuthProvider.LOCAL);
    given(userRepository.findById(userId)).willReturn(Optional.of(testUser));

    // when
    UserResponse response = userService.getMe(userId);

    // then
    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo(1L);
  }

  @Test
  @DisplayName("경계값: userId = Long.MAX_VALUE로 조회")
  void getMe_withMaxLongValue_throwsBusinessException() {
    // given
    Long userId = Long.MAX_VALUE;
    given(userRepository.findById(userId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> userService.getMe(userId))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("에러: 사용자 조회 실패 시 USER_NOT_FOUND 에러코드 확인")
  void getMe_withInvalidUserId_errorCodeIsUserNotFound() {
    // given
    Long userId = 100L;
    given(userRepository.findById(userId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> userService.getMe(userId))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());
  }
}
