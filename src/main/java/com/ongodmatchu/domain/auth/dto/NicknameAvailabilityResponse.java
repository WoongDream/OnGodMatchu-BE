package com.ongodmatchu.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NicknameAvailabilityResponse(boolean available, String reason, String matched) {

  public static final String REASON_FORMAT = "format";
  public static final String REASON_DUPLICATE = "taken";
  public static final String REASON_FORBIDDEN = "forbidden";

  public static final NicknameAvailabilityResponse AVAILABLE =
      new NicknameAvailabilityResponse(true, null, null);

  public static NicknameAvailabilityResponse unavailable(String reason) {
    return new NicknameAvailabilityResponse(false, reason, null);
  }

  /** 차단 닉네임 — 어떤 패턴(matched) 때문에 막혔는지 함께 전달. */
  public static NicknameAvailabilityResponse forbidden(String matched) {
    return new NicknameAvailabilityResponse(false, REASON_FORBIDDEN, matched);
  }
}
