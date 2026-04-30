package com.ongodmatchu.domain.user.service;

import com.ongodmatchu.domain.user.dto.UserResponse;
import com.ongodmatchu.domain.user.dto.UserUpdateRequest;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.domain.user.validation.NicknameNormalizer;
import com.ongodmatchu.domain.user.validation.NicknamePolicy;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final NicknameNormalizer nicknameNormalizer;
  private final NicknamePolicy nicknamePolicy;

  @Transactional(readOnly = true)
  public UserResponse getMe(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    return UserResponse.from(user);
  }

  @Transactional
  public UserResponse updateMe(Long userId, UserUpdateRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    String nickname = nicknameNormalizer.normalize(request.nickname());
    nicknamePolicy.enforce(nickname);

    if (!user.getNickname().equals(nickname) && userRepository.existsByNickname(nickname)) {
      throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
    }

    user.updateNickname(nickname);
    try {
      userRepository.flush();
    } catch (DataIntegrityViolationException e) {
      String message = e.getMostSpecificCause().getMessage();
      if (message != null && message.toLowerCase().contains("nickname")) {
        throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
      }
      throw e;
    }
    return UserResponse.from(user);
  }
}
