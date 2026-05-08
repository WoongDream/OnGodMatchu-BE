package com.ongodmatchu.global.exception;

import com.ongodmatchu.global.response.ApiResponse;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
    return ResponseEntity.status(errorCode.getStatus())
        .body(ApiResponse.fail(errorCode.getCode(), e.getMessage()));
  }

  @ExceptionHandler(RateLimitException.class)
  public ResponseEntity<ApiResponse<Void>> handleRateLimitException(RateLimitException e) {
    ErrorCode errorCode = ErrorCode.RATE_LIMITED;
    return ResponseEntity.status(errorCode.getStatus())
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
        .body(
            ApiResponse.fail(
                errorCode.getCode(), errorCode.getMessage(), e.getRetryAfterSeconds()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(
      MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
    return ResponseEntity.badRequest()
        .body(ApiResponse.fail(ErrorCode.INVALID_INPUT.getCode(), message));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.fail(ErrorCode.INVALID_INPUT.getCode(), "요청 본문을 읽을 수 없습니다."));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.fail("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException e) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(ApiResponse.fail("METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    log.error("Unhandled exception", e);
    return ResponseEntity.internalServerError()
        .body(ApiResponse.fail("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."));
  }
}
