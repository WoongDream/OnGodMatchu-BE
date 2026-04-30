package com.ongodmatchu.global.exception;

import com.ongodmatchu.global.response.ApiResponse;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
    return ResponseEntity.status(errorCode.getStatus())
        .body(ApiResponse.fail(errorCode.getCode(), e.getMessage()));
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

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    return ResponseEntity.internalServerError()
        .body(ApiResponse.fail("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."));
  }
}
