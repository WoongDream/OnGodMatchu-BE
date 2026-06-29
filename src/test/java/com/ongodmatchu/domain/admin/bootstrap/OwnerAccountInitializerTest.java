package com.ongodmatchu.domain.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ongodmatchu.domain.auth.validation.TermsPolicy;
import com.ongodmatchu.domain.user.entity.AdminAccount;
import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.Role;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OwnerAccountInitializerTest {

  @InjectMocks private OwnerAccountInitializer initializer;
  @Mock private UserRepository userRepository;
  @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(initializer, "ownerEmail", "owner@ongodmatchu.com");
    ReflectionTestUtils.setField(initializer, "ownerPassword", "raw-password");
  }

  /** 시스템 계정 초기 상태: role=USER, password=null, 약관 미동의. */
  private User buildSystemUser() {
    User user =
        User.builder()
            .email("system@ongodmatchu.com")
            .nickname(AdminAccount.NICKNAME)
            .password(null)
            .provider(AuthProvider.LOCAL)
            .emailVerified(true)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    ReflectionTestUtils.setField(user, "publicId", AdminAccount.PUBLIC_ID);
    ReflectionTestUtils.setField(user, "role", Role.USER);
    ReflectionTestUtils.setField(user, "isActive", true);
    return user;
  }

  @Test
  @DisplayName("ownerPassword 가 null 이면 아무것도 하지 않는다")
  void run_nullPassword_doesNothing() {
    ReflectionTestUtils.setField(initializer, "ownerPassword", null);

    initializer.run(null);

    verifyNoInteractions(userRepository, passwordEncoder);
  }

  @Test
  @DisplayName("ownerPassword 가 blank 면 아무것도 하지 않는다")
  void run_blankPassword_doesNothing() {
    ReflectionTestUtils.setField(initializer, "ownerPassword", "   ");

    initializer.run(null);

    verifyNoInteractions(userRepository, passwordEncoder);
  }

  @Test
  @DisplayName("findByPublicId 가 empty 면 인코더와 상호작용하지 않는다")
  void run_ownerNotFound_doesNothing() {
    given(userRepository.findByPublicId(AdminAccount.PUBLIC_ID)).willReturn(Optional.empty());

    initializer.run(null);

    verifyNoInteractions(passwordEncoder);
  }

  @Test
  @DisplayName("시스템 계정 보정: role=OWNER 승격 + 비밀번호 인코딩 + 약관 동의가 수행된다")
  void run_systemUser_bootstrapsOwner() {
    User owner = buildSystemUser();
    given(userRepository.findByPublicId(AdminAccount.PUBLIC_ID)).willReturn(Optional.of(owner));
    given(passwordEncoder.encode(any())).willReturn("ENC");

    initializer.run(null);

    assertThat(owner.getRole()).isEqualTo(Role.OWNER);
    assertThat(owner.getEmail()).isEqualTo("owner@ongodmatchu.com");
    assertThat(owner.getPassword()).isEqualTo("ENC");
    assertThat(owner.getTermsVersion()).isEqualTo(TermsPolicy.CURRENT_TERMS_VERSION);
    assertThat(owner.getPrivacyVersion()).isEqualTo(TermsPolicy.CURRENT_PRIVACY_VERSION);
    assertThat(owner.getTermsAgreedAt()).isNotNull();
    verify(passwordEncoder).encode("raw-password");
  }

  @Test
  @DisplayName("멱등: 이미 OWNER + 비밀번호 + 약관 동의 상태면 인코더를 호출하지 않는다")
  void run_alreadyBootstrapped_doesNotReEncode() {
    User owner = buildSystemUser();
    ReflectionTestUtils.setField(owner, "role", Role.OWNER);
    ReflectionTestUtils.setField(owner, "email", "owner@ongodmatchu.com");
    owner.updatePassword("EXISTING");
    owner.agreeToTerms(
        TermsPolicy.CURRENT_TERMS_VERSION, TermsPolicy.CURRENT_PRIVACY_VERSION, true);
    given(userRepository.findByPublicId(AdminAccount.PUBLIC_ID)).willReturn(Optional.of(owner));

    initializer.run(null);

    assertThat(owner.getPassword()).isEqualTo("EXISTING");
    verify(passwordEncoder, never()).encode(any());
  }
}
