package com.ongodmatchu.domain.admin.bootstrap;

import com.ongodmatchu.domain.auth.validation.TermsPolicy;
import com.ongodmatchu.domain.user.entity.AdminAccount;
import com.ongodmatchu.domain.user.entity.Role;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * OWNER(최상위 관리자) 부트스트랩. V31 마이그레이션이 기존 시스템 계정(AdminAccount.PUBLIC_ID)을 role=OWNER 로 승격하고, 여기서 환경변수로
 * 주입된 운영 이메일/비밀번호를 멱등하게 보정한다(로그인 가능화). 시크릿 미주입 시 아무것도 하지 않으므로 시스템 계정은 로그인 불가 상태로 유지된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerAccountInitializer implements ApplicationRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.owner.email:}")
  private String ownerEmail;

  @Value("${app.owner.password:}")
  private String ownerPassword;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (ownerPassword == null || ownerPassword.isBlank()) {
      return;
    }

    User owner = userRepository.findByPublicId(AdminAccount.PUBLIC_ID).orElse(null);
    if (owner == null) {
      return;
    }

    boolean changed = false;
    if (owner.getRole() != Role.OWNER) {
      owner.changeRole(Role.OWNER);
      changed = true;
    }
    if (!ownerEmail.isBlank() && !ownerEmail.equals(owner.getEmail())) {
      owner.assignOwnerEmail(ownerEmail);
      changed = true;
    }
    if (owner.getPassword() == null) {
      owner.updatePassword(passwordEncoder.encode(ownerPassword));
      changed = true;
    }
    // OWNER 는 약관 게이트(TermsAgreementInterceptor)를 통과해야 BO 접근 가능 — 미동의면 현재 버전으로 보정
    if (TermsPolicy.needsAgreement(owner.getTermsVersion(), owner.getPrivacyVersion())) {
      owner.agreeToTerms(
          TermsPolicy.CURRENT_TERMS_VERSION, TermsPolicy.CURRENT_PRIVACY_VERSION, true);
      changed = true;
    }
    if (changed) {
      log.info("OWNER 계정 부트스트랩 보정 완료 (publicId={})", AdminAccount.PUBLIC_ID);
    }
  }
}
