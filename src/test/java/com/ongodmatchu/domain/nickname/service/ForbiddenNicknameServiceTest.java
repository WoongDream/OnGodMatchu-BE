package com.ongodmatchu.domain.nickname.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ongodmatchu.domain.nickname.dto.ForbiddenNicknameCreateRequest;
import com.ongodmatchu.domain.nickname.dto.ForbiddenNicknameResponse;
import com.ongodmatchu.domain.nickname.dto.ForbiddenNicknameUpdateRequest;
import com.ongodmatchu.domain.nickname.dto.NicknameRuleStatsResponse;
import com.ongodmatchu.domain.nickname.entity.ForbiddenNickname;
import com.ongodmatchu.domain.nickname.entity.ForbiddenNicknameType;
import com.ongodmatchu.domain.nickname.entity.NicknameMatchType;
import com.ongodmatchu.domain.nickname.repository.ForbiddenNicknameRepository;
import com.ongodmatchu.domain.nickname.validation.NicknameMatchNormalizer;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ForbiddenNicknameServiceTest {

  @InjectMocks private ForbiddenNicknameService forbiddenNicknameService;
  @Mock private ForbiddenNicknameRepository repository;
  @Mock private NicknameMatchNormalizer matchNormalizer;

  private ForbiddenNickname rule(
      long id, String raw, String normalized, ForbiddenNicknameType type, NicknameMatchType match) {
    ForbiddenNickname f =
        ForbiddenNickname.builder()
            .rawValue(raw)
            .normalizedValue(normalized)
            .type(type)
            .matchType(match)
            .build();
    ReflectionTestUtils.setField(f, "id", id);
    return f;
  }

  private ErrorCode errorCodeOf(Throwable t) {
    return ((BusinessException) t).getErrorCode();
  }

  // ============ isBlocked / assertAllowed ============

  @Test
  @DisplayName("isBlocked_정규화후차단규칙일치_true")
  void isBlocked_matches_returnsTrue() {
    given(matchNormalizer.normalize("관리자")).willReturn("관리자");
    given(repository.existsBlocking("관리자")).willReturn(true);

    assertThat(forbiddenNicknameService.isBlocked("관리자")).isTrue();
  }

  @Test
  @DisplayName("isBlocked_정규화결과blank이면_차단아님_existsBlocking미호출")
  void isBlocked_blankNormalized_returnsFalseAndSkipsRepo() {
    given(matchNormalizer.normalize("...")).willReturn("");

    assertThat(forbiddenNicknameService.isBlocked("...")).isFalse();
    then(repository).should(never()).existsBlocking(anyString());
  }

  @Test
  @DisplayName("assertAllowed_차단닉네임이면_NICKNAME_FORBIDDEN")
  void assertAllowed_blocked_throws() {
    given(matchNormalizer.normalize("병신")).willReturn("병신");
    given(repository.existsBlocking("병신")).willReturn(true);

    assertThatThrownBy(() -> forbiddenNicknameService.assertAllowed("병신"))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.NICKNAME_FORBIDDEN);
  }

  @Test
  @DisplayName("assertAllowed_정규화blank이면_통과(예외없음)")
  void assertAllowed_blankNormalized_passes() {
    given(matchNormalizer.normalize("___")).willReturn("");

    forbiddenNicknameService.assertAllowed("___");

    then(repository).should(never()).existsBlocking(anyString());
  }

  @Test
  @DisplayName("assertAllowed_차단아니면_통과")
  void assertAllowed_notBlocked_passes() {
    given(matchNormalizer.normalize("정상닉네임")).willReturn("정상닉네임");
    given(repository.existsBlocking("정상닉네임")).willReturn(false);

    forbiddenNicknameService.assertAllowed("정상닉네임");
  }

  // ============ findBlockedTerm ============

  @Test
  @DisplayName("findBlockedTerm_차단되면_매칭규칙의_표시용원본반환")
  void findBlockedTerm_blocked_returnsRawValue() {
    given(matchNormalizer.normalize("나는최고병신")).willReturn("나는최고병신");
    given(repository.findBlocking(eq("나는최고병신"), any()))
        .willReturn(
            List.of(
                rule(1L, "병신", "병신", ForbiddenNicknameType.FORBIDDEN, NicknameMatchType.CONTAINS)));

    assertThat(forbiddenNicknameService.findBlockedTerm("나는최고병신")).isEqualTo("병신");
  }

  @Test
  @DisplayName("findBlockedTerm_차단아니면_null")
  void findBlockedTerm_notBlocked_returnsNull() {
    given(matchNormalizer.normalize("정상닉네임")).willReturn("정상닉네임");
    given(repository.findBlocking(eq("정상닉네임"), any())).willReturn(List.of());

    assertThat(forbiddenNicknameService.findBlockedTerm("정상닉네임")).isNull();
  }

  @Test
  @DisplayName("findBlockedTerm_정규화blank이면_null_repo미호출")
  void findBlockedTerm_blankNormalized_returnsNull() {
    given(matchNormalizer.normalize("...")).willReturn("");

    assertThat(forbiddenNicknameService.findBlockedTerm("...")).isNull();
    then(repository).should(never()).findBlocking(anyString(), any());
  }

  // ============ getRules ============

  @Test
  @DisplayName("getRules_filter type와 trim된 query위임_size50초과는cap")
  void getRules_delegatesAndCapsPageSize() {
    ForbiddenNickname f =
        rule(1L, "관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    given(repository.searchForAdmin(eq(ForbiddenNicknameType.FORBIDDEN), eq("hong"), any()))
        .willReturn(new PageImpl<>(List.of(f)));

    Page<ForbiddenNicknameResponse> result =
        forbiddenNicknameService.getRules(
            NicknameRuleFilter.FORBIDDEN, "  hong  ", PageRequest.of(0, 200));

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).value()).isEqualTo("관리자");

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(repository)
        .should()
        .searchForAdmin(eq(ForbiddenNicknameType.FORBIDDEN), eq("hong"), captor.capture());
    assertThat(captor.getValue().getPageSize()).isEqualTo(50);
  }

  @Test
  @DisplayName("getRules_빈query는null로_ALL필터는type null로위임")
  void getRules_blankQueryNullAndAllFilter() {
    given(repository.searchForAdmin(eq(null), eq(null), any()))
        .willReturn(new PageImpl<>(List.of()));

    forbiddenNicknameService.getRules(NicknameRuleFilter.ALL, "   ", PageRequest.of(0, 20));

    then(repository).should().searchForAdmin(eq(null), eq(null), any());
  }

  // ============ getStats ============

  @Test
  @DisplayName("getStats_countByType결과를_NicknameRuleStatsResponse로매핑")
  void getStats_mapsCounts() {
    given(repository.count()).willReturn(10L);
    given(repository.countByType(ForbiddenNicknameType.FORBIDDEN)).willReturn(4L);
    given(repository.countByType(ForbiddenNicknameType.RESERVED)).willReturn(6L);

    NicknameRuleStatsResponse result = forbiddenNicknameService.getStats();

    assertThat(result.total()).isEqualTo(10L);
    assertThat(result.forbidden()).isEqualTo(4L);
    assertThat(result.reserved()).isEqualTo(6L);
  }

  // ============ getRule ============

  @Test
  @DisplayName("getRule_존재하면_응답매핑")
  void getRule_found() {
    given(repository.findById(7L))
        .willReturn(
            Optional.of(
                rule(7L, "관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT)));

    ForbiddenNicknameResponse result = forbiddenNicknameService.getRule(7L);

    assertThat(result.id()).isEqualTo(7L);
    assertThat(result.value()).isEqualTo("관리자");
    assertThat(result.type()).isEqualTo("RESERVED");
    assertThat(result.matchType()).isEqualTo("EXACT");
  }

  @Test
  @DisplayName("getRule_미존재시_FORBIDDEN_NICKNAME_NOT_FOUND")
  void getRule_notFound() {
    given(repository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> forbiddenNicknameService.getRule(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.FORBIDDEN_NICKNAME_NOT_FOUND);
  }

  // ============ create ============

  @Test
  @DisplayName("create_정상_정규화후저장_응답매핑")
  void create_success() {
    given(matchNormalizer.normalize("관리자")).willReturn("관리자");
    given(repository.existsByNormalizedValueAndMatchType("관리자", NicknameMatchType.EXACT))
        .willReturn(false);
    given(repository.save(any(ForbiddenNickname.class))).willAnswer(inv -> inv.getArgument(0));

    ForbiddenNicknameResponse result =
        forbiddenNicknameService.create(
            new ForbiddenNicknameCreateRequest(
                "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT, "사칭 방지"));

    ArgumentCaptor<ForbiddenNickname> captor = ArgumentCaptor.forClass(ForbiddenNickname.class);
    then(repository).should().save(captor.capture());
    ForbiddenNickname saved = captor.getValue();
    assertThat(saved.getRawValue()).isEqualTo("관리자");
    assertThat(saved.getNormalizedValue()).isEqualTo("관리자");
    assertThat(saved.getType()).isEqualTo(ForbiddenNicknameType.RESERVED);
    assertThat(saved.getMatchType()).isEqualTo(NicknameMatchType.EXACT);
    assertThat(saved.getReason()).isEqualTo("사칭 방지");
    assertThat(result.value()).isEqualTo("관리자");
  }

  @Test
  @DisplayName("create_중복_FORBIDDEN_NICKNAME_DUPLICATE_저장미호출")
  void create_duplicate_throws() {
    given(matchNormalizer.normalize("관리자")).willReturn("관리자");
    given(repository.existsByNormalizedValueAndMatchType("관리자", NicknameMatchType.EXACT))
        .willReturn(true);

    assertThatThrownBy(
            () ->
                forbiddenNicknameService.create(
                    new ForbiddenNicknameCreateRequest(
                        "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.FORBIDDEN_NICKNAME_DUPLICATE);

    then(repository).should(never()).save(any());
  }

  @Test
  @DisplayName("create_정규화후blank_INVALID_INPUT_저장미호출")
  void create_normalizedBlank_throws() {
    given(matchNormalizer.normalize("...")).willReturn("");

    assertThatThrownBy(
            () ->
                forbiddenNicknameService.create(
                    new ForbiddenNicknameCreateRequest(
                        "...", ForbiddenNicknameType.FORBIDDEN, NicknameMatchType.CONTAINS, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.INVALID_INPUT);

    then(repository).should(never()).save(any());
  }

  // ============ update ============

  @Test
  @DisplayName("update_정상_필드갱신_응답매핑")
  void update_success() {
    ForbiddenNickname existing =
        rule(3L, "관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    given(repository.findById(3L)).willReturn(Optional.of(existing));
    given(matchNormalizer.normalize("운영자")).willReturn("운영자");
    given(repository.findByNormalizedValueAndMatchType("운영자", NicknameMatchType.PREFIX))
        .willReturn(Optional.empty());

    ForbiddenNicknameResponse result =
        forbiddenNicknameService.update(
            3L,
            new ForbiddenNicknameUpdateRequest(
                "운영자", ForbiddenNicknameType.RESERVED, NicknameMatchType.PREFIX, "수정사유"));

    assertThat(existing.getRawValue()).isEqualTo("운영자");
    assertThat(existing.getNormalizedValue()).isEqualTo("운영자");
    assertThat(existing.getMatchType()).isEqualTo(NicknameMatchType.PREFIX);
    assertThat(existing.getReason()).isEqualTo("수정사유");
    assertThat(result.value()).isEqualTo("운영자");
  }

  @Test
  @DisplayName("update_본인행과중복은허용(자기자신제외)")
  void update_sameRowNotDuplicate() {
    ForbiddenNickname existing =
        rule(3L, "관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    given(repository.findById(3L)).willReturn(Optional.of(existing));
    given(matchNormalizer.normalize("관리자")).willReturn("관리자");
    // 같은 정규화값+매칭의 행이 자기 자신이면 중복 아님
    given(repository.findByNormalizedValueAndMatchType("관리자", NicknameMatchType.EXACT))
        .willReturn(Optional.of(existing));

    forbiddenNicknameService.update(
        3L,
        new ForbiddenNicknameUpdateRequest(
            "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT, "사유"));

    assertThat(existing.getReason()).isEqualTo("사유");
  }

  @Test
  @DisplayName("update_다른행과중복_FORBIDDEN_NICKNAME_DUPLICATE")
  void update_duplicateOtherRow_throws() {
    ForbiddenNickname existing =
        rule(3L, "관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    ForbiddenNickname other =
        rule(9L, "운영자", "운영자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    given(repository.findById(3L)).willReturn(Optional.of(existing));
    given(matchNormalizer.normalize("운영자")).willReturn("운영자");
    given(repository.findByNormalizedValueAndMatchType("운영자", NicknameMatchType.EXACT))
        .willReturn(Optional.of(other));

    assertThatThrownBy(
            () ->
                forbiddenNicknameService.update(
                    3L,
                    new ForbiddenNicknameUpdateRequest(
                        "운영자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.FORBIDDEN_NICKNAME_DUPLICATE);
  }

  @Test
  @DisplayName("update_미존재_FORBIDDEN_NICKNAME_NOT_FOUND")
  void update_notFound() {
    given(repository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                forbiddenNicknameService.update(
                    99L,
                    new ForbiddenNicknameUpdateRequest(
                        "x", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.FORBIDDEN_NICKNAME_NOT_FOUND);
  }

  // ============ delete ============

  @Test
  @DisplayName("delete_존재하면_repository.delete호출")
  void delete_success() {
    ForbiddenNickname existing =
        rule(6L, "관리자", "관리자", ForbiddenNicknameType.RESERVED, NicknameMatchType.EXACT);
    given(repository.findById(6L)).willReturn(Optional.of(existing));

    forbiddenNicknameService.delete(6L);

    then(repository).should().delete(existing);
  }

  @Test
  @DisplayName("delete_미존재_FORBIDDEN_NICKNAME_NOT_FOUND_delete미호출")
  void delete_notFound() {
    given(repository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> forbiddenNicknameService.delete(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(this::errorCodeOf)
        .isEqualTo(ErrorCode.FORBIDDEN_NICKNAME_NOT_FOUND);
    then(repository).should(never()).delete(any());
  }
}
