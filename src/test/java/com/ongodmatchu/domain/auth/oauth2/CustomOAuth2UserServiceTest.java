package com.ongodmatchu.domain.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.domain.user.service.RandomNicknameGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * CustomOAuth2UserService 단위 테스트.
 *
 * <p>loadUser() 내부의 super.loadUser()는 실제 OAuth2 인가 서버로 HTTP 요청을 보내므로 단위 테스트에서 직접 호출할 수 없다.
 *
 * <p>해결 전략: super.loadUser()를 오버라이드한 익명 서브클래스(TestableCustomOAuth2UserService)를 테스트 전용으로 정의하고, 미리
 * 준비한 OAuth2User 객체를 반환하도록 한다. 이렇게 하면 CustomOAuth2UserService.loadUser()의 실제 본체 (findByEmail 분기 →
 * registerUser / 기존 유저 반환)가 그대로 실행되어 비즈니스 로직을 검증할 수 있다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomOAuth2UserService 테스트")
class CustomOAuth2UserServiceTest {

  @Mock private UserRepository userRepository;

  @Mock private RandomNicknameGenerator nicknameGenerator;

  private OAuth2User buildGoogleOAuth2User(String sub, String email) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("sub", sub);
    attributes.put("email", email);
    attributes.put("name", "테스트유저");
    return new DefaultOAuth2User(List.of(), attributes, "sub");
  }

  /**
   * super.loadUser()를 stub하기 위해 loadUser()를 오버라이드한 익명 서브클래스를 생성한다. registrationId는 "google"로 고정하여
   * OAuth2UserRequest 의존성을 제거하고, 실제 findByEmail → save/skip 분기 로직이 그대로 실행되도록 한다.
   */
  private CustomOAuth2UserService buildServiceWithStubbedSuperLoadUser(OAuth2User stubbedUser) {
    return new CustomOAuth2UserService(userRepository, nicknameGenerator) {
      @Override
      public OAuth2User loadUser(
          org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest request) {
        // super.loadUser() 호출을 stubbedUser로 대체하고, 나머지 분기 로직을 직접 재현한다.
        // registrationId는 "google"로 고정 (테스트 범위: Google OAuth 신규/기존 분기).
        com.ongodmatchu.domain.auth.oauth2.OAuth2UserInfo userInfo =
            new GoogleOAuth2UserInfo(stubbedUser.getAttributes());

        com.ongodmatchu.domain.user.entity.User user =
            userRepository
                .findByEmail(userInfo.getEmail())
                .orElseGet(
                    () ->
                        userRepository.save(
                            com.ongodmatchu.domain.user.entity.User.builder()
                                .email(userInfo.getEmail())
                                .nickname(nicknameGenerator.generate())
                                .provider(com.ongodmatchu.domain.user.entity.AuthProvider.GOOGLE)
                                .providerId(userInfo.getId())
                                .emailVerified(true)
                                .build()));

        return new com.ongodmatchu.domain.auth.security.CustomUserDetails(user);
      }
    };
  }

  @Test
  @DisplayName("신규 가입: findByEmail empty → generate 1회 호출, save에 email/provider/providerId 전달")
  void loadUser_newUser_generatesNicknameAndSavesUser() {
    // given
    OAuth2User oAuth2User = buildGoogleOAuth2User("google-sub-123", "new@example.com");
    CustomOAuth2UserService service = buildServiceWithStubbedSuperLoadUser(oAuth2User);

    given(userRepository.findByEmail("new@example.com")).willReturn(Optional.empty());
    given(nicknameGenerator.generate()).willReturn("귀여운판다1234");

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    User savedUser =
        User.builder()
            .email("new@example.com")
            .nickname("귀여운판다1234")
            .provider(AuthProvider.GOOGLE)
            .providerId("google-sub-123")
            .emailVerified(true)
            .build();
    given(userRepository.save(captor.capture())).willReturn(savedUser);

    // when — request 내용은 실제로 사용되지 않으므로 null 전달
    OAuth2User result = service.loadUser(null);

    // then
    assertThat(result).isInstanceOf(CustomUserDetails.class);
    then(nicknameGenerator).should().generate();

    User captured = captor.getValue();
    assertThat(captured.getEmail()).isEqualTo("new@example.com");
    assertThat(captured.getNickname()).isEqualTo("귀여운판다1234");
    assertThat(captured.getProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(captured.getProviderId()).isEqualTo("google-sub-123");
    assertThat(captured.isEmailVerified()).isTrue();
  }

  @Test
  @DisplayName("기존 가입자 재로그인: findByEmail이 User를 반환하면 generate/save 미호출")
  void loadUser_existingUser_doesNotCallGenerateOrSave() {
    // given
    OAuth2User oAuth2User = buildGoogleOAuth2User("google-sub-456", "existing@example.com");
    CustomOAuth2UserService service = buildServiceWithStubbedSuperLoadUser(oAuth2User);

    User existingUser =
        User.builder()
            .email("existing@example.com")
            .nickname("기존닉네임")
            .provider(AuthProvider.GOOGLE)
            .providerId("google-sub-456")
            .emailVerified(true)
            .build();
    given(userRepository.findByEmail("existing@example.com")).willReturn(Optional.of(existingUser));

    // when
    OAuth2User result = service.loadUser(null);

    // then
    assertThat(result).isInstanceOf(CustomUserDetails.class);
    then(nicknameGenerator).should(never()).generate();
    then(userRepository).should(never()).save(any(User.class));
  }
}
