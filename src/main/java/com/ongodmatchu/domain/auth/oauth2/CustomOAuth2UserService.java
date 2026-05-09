package com.ongodmatchu.domain.auth.oauth2;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.domain.auth.validation.TermsPolicy;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.domain.user.service.ProfileImageInitializer;
import com.ongodmatchu.domain.user.service.RandomNicknameGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private final UserRepository userRepository;
  private final RandomNicknameGenerator nicknameGenerator;
  private final ProfileImageInitializer profileImageInitializer;

  @Override
  @Transactional
  public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
    OAuth2User oAuth2User = super.loadUser(request);
    String registrationId = request.getClientRegistration().getRegistrationId();
    OAuth2UserInfo userInfo = resolveUserInfo(registrationId, oAuth2User.getAttributes());

    User user =
        userRepository
            .findByEmail(userInfo.getEmail())
            .orElseGet(() -> registerUser(userInfo, registrationId));

    return new CustomUserDetails(user);
  }

  private OAuth2UserInfo resolveUserInfo(
      String registrationId, java.util.Map<String, Object> attributes) {
    return switch (registrationId) {
      case "google" -> new GoogleOAuth2UserInfo(attributes);
      case "naver" -> new NaverOAuth2UserInfo(attributes);
      case "kakao" -> new KakaoOAuth2UserInfo(attributes);
      default -> throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인: " + registrationId);
    };
  }

  private User registerUser(OAuth2UserInfo userInfo, String registrationId) {
    User user =
        User.builder()
            .email(userInfo.getEmail())
            .nickname(nicknameGenerator.generate())
            .provider(AuthProvider.valueOf(registrationId.toUpperCase()))
            .providerId(userInfo.getId())
            .emailVerified(true)
            .build();
    // TODO: OAuth 첫 가입자도 약관 동의 페이지를 거치도록 후속 작업 (현재는 자동 동의 처리).
    user.agreeToTerms(
        TermsPolicy.CURRENT_TERMS_VERSION, TermsPolicy.CURRENT_PRIVACY_VERSION, false);
    userRepository.saveAndFlush(user);
    profileImageInitializer.initialize(user);
    return user;
  }
}
