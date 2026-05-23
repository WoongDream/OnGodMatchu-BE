package com.ongodmatchu.domain.auth.security;

import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    ensureActive(user);
    return new CustomUserDetails(user);
  }

  @Transactional(readOnly = true)
  public UserDetails loadUserById(Long id) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    ensureActive(user);
    return new CustomUserDetails(user);
  }

  /** 탈퇴/시스템 유저는 인증 단계에서 차단 — 토큰이 살아있어도 401. */
  private void ensureActive(User user) {
    if (!user.isActive() || user.isSystem()) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
  }
}
