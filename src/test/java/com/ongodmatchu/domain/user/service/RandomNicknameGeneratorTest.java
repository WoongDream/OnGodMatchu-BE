package com.ongodmatchu.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeast;

import com.ongodmatchu.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RandomNicknameGenerator 테스트")
class RandomNicknameGeneratorTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private RandomNicknameGenerator nicknameGenerator;

  @Test
  @DisplayName("정상: existsByNickname이 false를 반환하면 첫 시도에서 형용사+명사+4자리 숫자 형식 닉네임 반환")
  void generate_noConflict_returnsFormattedNickname() {
    // given
    given(userRepository.existsByNickname(anyString())).willReturn(false);

    // when
    String result = nicknameGenerator.generate();

    // then
    assertThat(result).isNotNull();
    assertThat(result).matches(".*\\d{4}$");
    String digits = result.substring(result.length() - 4);
    int number = Integer.parseInt(digits);
    assertThat(number).isBetween(1000, 9999);
  }

  @Test
  @DisplayName("충돌 후 재시도: 첫 호출에서 true, 이후 false를 반환하면 닉네임을 반환하고 existsByNickname이 2회 이상 호출됨")
  void generate_firstConflictThenSuccess_retriesAndReturnsNickname() {
    // given
    given(userRepository.existsByNickname(anyString())).willReturn(true, false);

    // when
    String result = nicknameGenerator.generate();

    // then
    assertThat(result).isNotNull();
    assertThat(result).matches(".*\\d{4}$");
    then(userRepository).should(atLeast(2)).existsByNickname(anyString());
  }

  @Test
  @DisplayName("전체 충돌: existsByNickname이 항상 true를 반환하면 fallback '유저'+timestamp 형식 반환")
  void generate_allConflicts_returnsFallbackNickname() {
    // given
    given(userRepository.existsByNickname(anyString())).willReturn(true);

    // when
    String result = nicknameGenerator.generate();

    // then
    assertThat(result).startsWith("유저");
  }
}
