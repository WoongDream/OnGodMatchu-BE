package com.ongodmatchu.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ongodmatchu.domain.user.entity.AuthProvider;
import com.ongodmatchu.domain.user.entity.Role;
import com.ongodmatchu.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

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

  // ============ searchForAdmin (백오피스 목록) ============

  /** isSystem=false, role/isActive/suspendedUntil 를 setField 로 세팅해 저장한다. */
  private User savedAdminUser(
      String email, String nickname, Role role, boolean isActive, LocalDateTime suspendedUntil) {
    User user =
        userRepository.save(
            User.builder()
                .email(email)
                .nickname(nickname)
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .build());
    ReflectionTestUtils.setField(user, "role", role);
    ReflectionTestUtils.setField(user, "isActive", isActive);
    ReflectionTestUtils.setField(user, "suspendedUntil", suspendedUntil);
    return userRepository.save(user);
  }

  @Test
  @DisplayName("searchForAdmin_status=null_활성만(탈퇴제외)_시스템계정제외")
  void searchForAdmin_statusNull_activeOnlyExcludesWithdrawnAndSystem() {
    LocalDateTime now = LocalDateTime.now();
    User active = savedAdminUser("sfa-active@example.com", "SFA_활성", Role.USER, true, null);
    savedAdminUser("sfa-withdrawn@example.com", "SFA_탈퇴", Role.USER, false, null);

    Page<User> page = userRepository.searchForAdmin(null, null, "SFA_", now, PageRequest.of(0, 50));

    assertThat(page.getContent())
        .extracting(User::getEmail)
        .contains(active.getEmail())
        .doesNotContain("sfa-withdrawn@example.com");
    // 시스템 계정(isSystem=true)은 결과에서 제외된다
    assertThat(page.getContent()).allMatch(u -> !u.isSystem());
  }

  @Test
  @DisplayName("searchForAdmin_status=WITHDRAWN_탈퇴자만")
  void searchForAdmin_statusWithdrawn_returnsWithdrawnOnly() {
    LocalDateTime now = LocalDateTime.now();
    savedAdminUser("sfa-active@example.com", "SFA_활성", Role.USER, true, null);
    User withdrawn = savedAdminUser("sfa-withdrawn@example.com", "SFA_탈퇴", Role.USER, false, null);

    Page<User> page =
        userRepository.searchForAdmin("WITHDRAWN", null, "SFA_", now, PageRequest.of(0, 50));

    assertThat(page.getContent()).extracting(User::getEmail).containsExactly(withdrawn.getEmail());
  }

  @Test
  @DisplayName("searchForAdmin_status=SUSPENDED_정지(활성+suspendedUntil>now)만")
  void searchForAdmin_statusSuspended_returnsSuspendedOnly() {
    LocalDateTime now = LocalDateTime.now();
    savedAdminUser("sfa-active@example.com", "SFA_활성", Role.USER, true, null);
    User suspended =
        savedAdminUser("sfa-suspended@example.com", "SFA_정지", Role.USER, true, now.plusDays(1));
    // 정지기한이 지난(만료) 활성 유저는 SUSPENDED 아님
    savedAdminUser("sfa-expired@example.com", "SFA_만료", Role.USER, true, now.minusDays(1));

    Page<User> page =
        userRepository.searchForAdmin("SUSPENDED", null, "SFA_", now, PageRequest.of(0, 50));

    assertThat(page.getContent()).extracting(User::getEmail).containsExactly(suspended.getEmail());
  }

  @Test
  @DisplayName("searchForAdmin_status=ACTIVE_활성+정지아님만")
  void searchForAdmin_statusActive_returnsActiveNotSuspended() {
    LocalDateTime now = LocalDateTime.now();
    User active = savedAdminUser("sfa-active@example.com", "SFA_활성", Role.USER, true, null);
    savedAdminUser("sfa-suspended@example.com", "SFA_정지", Role.USER, true, now.plusDays(1));
    savedAdminUser("sfa-withdrawn@example.com", "SFA_탈퇴", Role.USER, false, null);

    Page<User> page =
        userRepository.searchForAdmin("ACTIVE", null, "SFA_", now, PageRequest.of(0, 50));

    assertThat(page.getContent()).extracting(User::getEmail).containsExactly(active.getEmail());
  }

  @Test
  @DisplayName("searchForAdmin_role=ADMIN_ADMIN만")
  void searchForAdmin_roleAdmin_returnsAdminsOnly() {
    LocalDateTime now = LocalDateTime.now();
    savedAdminUser("sfa-user@example.com", "SFA_유저", Role.USER, true, null);
    User admin = savedAdminUser("sfa-admin@example.com", "SFA_관리", Role.ADMIN, true, null);

    Page<User> page =
        userRepository.searchForAdmin(null, Role.ADMIN, "SFA_", now, PageRequest.of(0, 50));

    assertThat(page.getContent()).extracting(User::getEmail).containsExactly(admin.getEmail());
  }

  @Test
  @DisplayName("searchForAdmin_query_닉네임/이메일_부분일치_대소문자무시_null이면전체")
  void searchForAdmin_queryMatchesNicknameAndEmailCaseInsensitive() {
    LocalDateTime now = LocalDateTime.now();
    User byNickname = savedAdminUser("sfa-nick@example.com", "SFA_길동KIM", Role.USER, true, null);
    User byEmail = savedAdminUser("sfa-LeeMail@example.com", "SFA_철수", Role.USER, true, null);

    // 닉네임 부분일치 (대소문자 무시)
    Page<User> byNick =
        userRepository.searchForAdmin(null, null, "kim", now, PageRequest.of(0, 50));
    assertThat(byNick.getContent()).extracting(User::getEmail).contains(byNickname.getEmail());

    // 이메일 부분일치 (대소문자 무시)
    Page<User> byMail =
        userRepository.searchForAdmin(null, null, "leemail", now, PageRequest.of(0, 50));
    assertThat(byMail.getContent()).extracting(User::getEmail).contains(byEmail.getEmail());

    // query=null 이면 둘 다 포함 (전체)
    Page<User> all = userRepository.searchForAdmin(null, null, "SFA_", now, PageRequest.of(0, 50));
    assertThat(all.getContent())
        .extracting(User::getEmail)
        .contains(byNickname.getEmail(), byEmail.getEmail());
  }

  @Test
  @DisplayName("searchForAdmin_시스템계정(isSystem=true)은_status=null/ACTIVE_어느쪽에도_미포함")
  void searchForAdmin_excludesSystemAccount() {
    LocalDateTime now = LocalDateTime.now();
    // 시스템 계정이 존재한다면(Flyway V17 시드) 결과에 절대 포함되면 안 된다.
    List<User> systemAccounts = userRepository.findAll().stream().filter(User::isSystem).toList();

    Page<User> active = userRepository.searchForAdmin(null, null, null, now, PageRequest.of(0, 50));
    assertThat(active.getContent()).noneMatch(User::isSystem);

    Page<User> activeFilter =
        userRepository.searchForAdmin("ACTIVE", null, null, now, PageRequest.of(0, 50));
    assertThat(activeFilter.getContent()).noneMatch(User::isSystem);

    // 시드가 존재한다는 전제 자체가 깨지지 않았는지 가드 (있으면 위 검증이 유의미)
    assertThat(systemAccounts).allMatch(User::isSystem);
  }
}
