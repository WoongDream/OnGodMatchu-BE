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
import com.ongodmatchu.domain.user.service.ProfileImageInitializer;
import com.ongodmatchu.domain.user.service.RandomNicknameGenerator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RandomNicknameGenerator nicknameGenerator;
  @Mock private ProfileImageInitializer profileImageInitializer;

  private CustomOAuth2UserService service;

  @BeforeEach
  void setUp() {
    service =
        new CustomOAuth2UserService(userRepository, nicknameGenerator, profileImageInitializer) {
          @Override
          public OAuth2User loadUser(OAuth2UserRequest request) {
            // super.loadUser() 는 실제 HTTP 호출 — 익명 서브클래스로 우회
            OAuth2User oAuth2User = buildGoogleOAuth2User(request);
            String registrationId = request.getClientRegistration().getRegistrationId();
            OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(oAuth2User.getAttributes());

            User user =
                userRepository
                    .findByEmail(userInfo.getEmail())
                    .orElseGet(() -> registerUserForTest(userInfo, registrationId));

            return new CustomUserDetails(user);
          }

          private User registerUserForTest(OAuth2UserInfo userInfo, String registrationId) {
            User u =
                userRepository.saveAndFlush(
                    User.builder()
                        .email(userInfo.getEmail())
                        .nickname(nicknameGenerator.generate())
                        .provider(AuthProvider.valueOf(registrationId.toUpperCase()))
                        .providerId(userInfo.getId())
                        .emailVerified(true)
                        .build());
            profileImageInitializer.initialize(u);
            return u;
          }
        };
  }

  // ============ 헬퍼 ============

  private OAuth2UserRequest buildOAuth2UserRequest(String registrationId) {
    ClientRegistration clientRegistration =
        ClientRegistration.withRegistrationId(registrationId)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .clientId("client-id")
            .clientSecret("client-secret")
            .redirectUri("http://localhost/callback")
            .authorizationUri("http://auth.example.com/authorize")
            .tokenUri("http://auth.example.com/token")
            .userInfoUri("http://auth.example.com/userinfo")
            .userNameAttributeName("sub")
            .build();
    OAuth2AccessToken accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "token",
            java.time.Instant.now(),
            java.time.Instant.now().plusSeconds(3600));
    return new OAuth2UserRequest(clientRegistration, accessToken);
  }

  private OAuth2User buildGoogleOAuth2User(OAuth2UserRequest request) {
    Map<String, Object> attributes =
        Map.of(
            "sub", "google-sub-001",
            "email", "google@example.com",
            "name", "구글유저");
    return new DefaultOAuth2User(java.util.List.of(), attributes, "sub");
  }

  private User buildExistingUser(String email) {
    User user =
        User.builder()
            .email(email)
            .nickname("기존유저")
            .provider(AuthProvider.GOOGLE)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", 10L);
    ReflectionTestUtils.setField(user, "publicId", UUID.randomUUID());
    return user;
  }

  // ============ 신규 가입 분기 ============

  @Test
  @DisplayName("loadUser_신규가입_saveAndFlush호출_profileImageInitializer호출")
  void loadUser_newUser_savesAndInitializesProfileImage() {
    User savedUser =
        User.builder()
            .email("google@example.com")
            .nickname("랜덤닉네임1234")
            .provider(AuthProvider.GOOGLE)
            .providerId("google-sub-001")
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(savedUser, "id", 1L);
    ReflectionTestUtils.setField(savedUser, "publicId", UUID.randomUUID());

    given(userRepository.findByEmail("google@example.com")).willReturn(Optional.empty());
    given(nicknameGenerator.generate()).willReturn("랜덤닉네임1234");
    given(userRepository.saveAndFlush(any(User.class))).willReturn(savedUser);

    OAuth2UserRequest request = buildOAuth2UserRequest("google");
    service.loadUser(request);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    then(userRepository).should().saveAndFlush(captor.capture());
    User captured = captor.getValue();
    assertThat(captured.getEmail()).isEqualTo("google@example.com");
    assertThat(captured.getProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(captured.isEmailVerified()).isTrue();

    then(profileImageInitializer).should().initialize(savedUser);
  }

  @Test
  @DisplayName("loadUser_신규가입_반환값은CustomUserDetails")
  void loadUser_newUser_returnsCustomUserDetails() {
    User savedUser =
        User.builder()
            .email("google@example.com")
            .nickname("랜덤닉네임1234")
            .provider(AuthProvider.GOOGLE)
            .providerId("google-sub-001")
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(savedUser, "id", 1L);
    ReflectionTestUtils.setField(savedUser, "publicId", UUID.randomUUID());

    given(userRepository.findByEmail("google@example.com")).willReturn(Optional.empty());
    given(nicknameGenerator.generate()).willReturn("랜덤닉네임1234");
    given(userRepository.saveAndFlush(any(User.class))).willReturn(savedUser);

    OAuth2UserRequest request = buildOAuth2UserRequest("google");
    OAuth2User result = service.loadUser(request);

    assertThat(result).isInstanceOf(CustomUserDetails.class);
  }

  // ============ 기존 유저 분기 ============

  @Test
  @DisplayName("loadUser_기존유저_saveAndFlush미호출_profileImageInitializer미호출")
  void loadUser_existingUser_doesNotSaveOrInitialize() {
    User existingUser = buildExistingUser("google@example.com");
    given(userRepository.findByEmail("google@example.com")).willReturn(Optional.of(existingUser));

    OAuth2UserRequest request = buildOAuth2UserRequest("google");
    service.loadUser(request);

    then(userRepository).should(never()).saveAndFlush(any());
    then(profileImageInitializer).should(never()).initialize(any());
  }

  @Test
  @DisplayName("loadUser_기존유저_기존User반환")
  void loadUser_existingUser_returnsExistingUser() {
    User existingUser = buildExistingUser("google@example.com");
    given(userRepository.findByEmail("google@example.com")).willReturn(Optional.of(existingUser));

    OAuth2UserRequest request = buildOAuth2UserRequest("google");
    OAuth2User result = service.loadUser(request);

    assertThat(result).isInstanceOf(CustomUserDetails.class);
    CustomUserDetails details = (CustomUserDetails) result;
    assertThat(details.getUser().getEmail()).isEqualTo("google@example.com");
  }
}
