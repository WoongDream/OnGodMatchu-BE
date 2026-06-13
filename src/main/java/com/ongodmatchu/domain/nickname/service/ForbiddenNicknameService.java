package com.ongodmatchu.domain.nickname.service;

import com.ongodmatchu.domain.nickname.dto.ForbiddenNicknameCreateRequest;
import com.ongodmatchu.domain.nickname.dto.ForbiddenNicknameResponse;
import com.ongodmatchu.domain.nickname.dto.ForbiddenNicknameUpdateRequest;
import com.ongodmatchu.domain.nickname.dto.NicknameRuleStatsResponse;
import com.ongodmatchu.domain.nickname.entity.ForbiddenNickname;
import com.ongodmatchu.domain.nickname.entity.ForbiddenNicknameType;
import com.ongodmatchu.domain.nickname.repository.ForbiddenNicknameRepository;
import com.ongodmatchu.domain.nickname.validation.NicknameMatchNormalizer;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차단 닉네임 관리(백오피스 CRUD) + 닉네임 입력 차단 검증. 검증은 가입/닉네임 변경/check-nickname 에서 호출. 매칭은 Tier 0·1 정규화 키
 * 기준(완전/접두/부분).
 */
@Service
@RequiredArgsConstructor
public class ForbiddenNicknameService {

  private static final int MAX_PAGE_SIZE = 50;

  private final ForbiddenNicknameRepository repository;
  private final NicknameMatchNormalizer matchNormalizer;

  // --- 닉네임 입력 차단 검증 ---

  /** 정규화 후 차단 규칙에 걸리면 true. 정규화 결과가 비면(문자·숫자 없음) 차단 대상 아님(형식 정책이 별도 거부). */
  @Transactional(readOnly = true)
  public boolean isBlocked(String nickname) {
    String candidate = matchNormalizer.normalize(nickname);
    return !candidate.isBlank() && repository.existsBlocking(candidate);
  }

  /**
   * 차단한 규칙의 표시용 원본(예: '병신', '관리자')을 반환 — 어느 단어 때문에 막혔는지 안내용. 차단 아니면 null. check-nickname 응답에 사용.
   */
  @Transactional(readOnly = true)
  public String findBlockedTerm(String nickname) {
    String candidate = matchNormalizer.normalize(nickname);
    if (candidate.isBlank()) {
      return null;
    }
    return repository.findBlocking(candidate, PageRequest.of(0, 1)).stream()
        .findFirst()
        .map(ForbiddenNickname::getRawValue)
        .orElse(null);
  }

  /** 차단 닉네임이면 {@code NICKNAME_FORBIDDEN} 던짐. 가입/닉네임 변경 경로에서 형식 검증 직후 호출. */
  public void assertAllowed(String nickname) {
    if (isBlocked(nickname)) {
      throw new BusinessException(ErrorCode.NICKNAME_FORBIDDEN);
    }
  }

  // --- 백오피스 CRUD ---

  @Transactional(readOnly = true)
  public Page<ForbiddenNicknameResponse> getRules(
      NicknameRuleFilter filter, String query, Pageable pageable) {
    String q = blankToNull(query);
    Pageable capped =
        PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
    return repository.searchForAdmin(filter.type(), q, capped).map(ForbiddenNicknameResponse::from);
  }

  @Transactional(readOnly = true)
  public NicknameRuleStatsResponse getStats() {
    long total = repository.count();
    long forbidden = repository.countByType(ForbiddenNicknameType.FORBIDDEN);
    long reserved = repository.countByType(ForbiddenNicknameType.RESERVED);
    return new NicknameRuleStatsResponse(total, forbidden, reserved);
  }

  @Transactional(readOnly = true)
  public ForbiddenNicknameResponse getRule(Long id) {
    return ForbiddenNicknameResponse.from(get(id));
  }

  @Transactional
  public ForbiddenNicknameResponse create(ForbiddenNicknameCreateRequest request) {
    String raw = request.value().trim();
    String normalized = normalizeOrThrow(raw);
    if (repository.existsByNormalizedValueAndMatchType(normalized, request.matchType())) {
      throw new BusinessException(ErrorCode.FORBIDDEN_NICKNAME_DUPLICATE);
    }
    ForbiddenNickname saved =
        repository.save(
            ForbiddenNickname.builder()
                .rawValue(raw)
                .normalizedValue(normalized)
                .type(request.type())
                .matchType(request.matchType())
                .reason(blankToNull(request.reason()))
                .build());
    return ForbiddenNicknameResponse.from(saved);
  }

  @Transactional
  public ForbiddenNicknameResponse update(Long id, ForbiddenNicknameUpdateRequest request) {
    ForbiddenNickname rule = get(id);
    String raw = request.value().trim();
    String normalized = normalizeOrThrow(raw);
    repository
        .findByNormalizedValueAndMatchType(normalized, request.matchType())
        .filter(other -> !other.getId().equals(id))
        .ifPresent(
            other -> {
              throw new BusinessException(ErrorCode.FORBIDDEN_NICKNAME_DUPLICATE);
            });
    rule.update(
        raw, normalized, request.type(), request.matchType(), blankToNull(request.reason()));
    return ForbiddenNicknameResponse.from(rule);
  }

  @Transactional
  public void delete(Long id) {
    repository.delete(get(id));
  }

  // --- helpers ---

  private String normalizeOrThrow(String raw) {
    String normalized = matchNormalizer.normalize(raw);
    if (normalized.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "유효한 닉네임 패턴이 아닙니다.");
    }
    return normalized;
  }

  private ForbiddenNickname get(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN_NICKNAME_NOT_FOUND));
  }

  private static String blankToNull(String value) {
    return (value == null || value.isBlank()) ? null : value.trim();
  }
}
