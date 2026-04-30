package com.ongodmatchu.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NicknameAvailabilityResponse(boolean available, String reason) {

  public static final String REASON_FORMAT = "format";
  public static final String REASON_DUPLICATE = "taken";

  public static final NicknameAvailabilityResponse AVAILABLE =
      new NicknameAvailabilityResponse(true, null);

  public static NicknameAvailabilityResponse unavailable(String reason) {
    return new NicknameAvailabilityResponse(false, reason);
  }
}
