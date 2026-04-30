package com.ongodmatchu.global.exception;

import lombok.Getter;

@Getter
public class RateLimitException extends RuntimeException {

  private final long retryAfterSeconds;

  public RateLimitException(long retryAfterSeconds) {
    super(ErrorCode.RATE_LIMITED.getMessage());
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
