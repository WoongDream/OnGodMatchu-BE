package com.ongodmatchu.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  // Common
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),

  // User
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
  EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다."),
  NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다."),
  INVALID_NICKNAME_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME_FORMAT", "닉네임 형식이 올바르지 않습니다."),
  INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "INVALID_PASSWORD", "비밀번호가 올바르지 않습니다."),
  EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "이메일 인증이 필요합니다."),
  SOCIAL_USER_PASSWORD_LOGIN(
      HttpStatus.BAD_REQUEST, "SOCIAL_USER_PASSWORD_LOGIN", "소셜 로그인 계정은 비밀번호 로그인을 사용할 수 없습니다."),
  PASSWORD_POLICY_VIOLATION(
      HttpStatus.BAD_REQUEST, "PASSWORD_POLICY_VIOLATION", "비밀번호 정책을 위반했습니다."),
  PASSWORD_BREACHED(
      HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_BREACHED", "외부에 유출된 이력이 있는 비밀번호입니다."),
  INVALID_CURRENT_PASSWORD(
      HttpStatus.UNAUTHORIZED, "INVALID_CURRENT_PASSWORD", "현재 비밀번호가 올바르지 않습니다."),
  OAUTH_USER_NO_PASSWORD(
      HttpStatus.BAD_REQUEST, "OAUTH_USER_NO_PASSWORD", "소셜 로그인 계정은 비밀번호 변경을 사용할 수 없습니다."),
  INVALID_BIO_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_BIO_FORMAT", "한 줄 소개 형식이 올바르지 않습니다."),
  INVALID_WITHDRAWAL_CONFIRMATION(
      HttpStatus.BAD_REQUEST, "INVALID_WITHDRAWAL_CONFIRMATION", "탈퇴 확인 문구가 올바르지 않습니다."),
  TERMS_AGREEMENT_REQUIRED(HttpStatus.BAD_REQUEST, "TERMS_AGREEMENT_REQUIRED", "필수 약관에 동의해야 합니다."),
  TERMS_AGREEMENT_OUTDATED(HttpStatus.FORBIDDEN, "TERMS_AGREEMENT_OUTDATED", "약관 동의가 필요합니다."),
  ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "정지된 계정은 해당 작업을 수행할 수 없습니다."),

  // Admin / RBAC
  ADMIN_FORBIDDEN(HttpStatus.FORBIDDEN, "ADMIN_FORBIDDEN", "해당 관리 작업에 대한 권한이 없습니다."),
  ADMIN_TARGET_INVALID(HttpStatus.BAD_REQUEST, "ADMIN_TARGET_INVALID", "관리 대상이 올바르지 않습니다."),

  // Email verification
  INVALID_VERIFICATION_CODE(
      HttpStatus.BAD_REQUEST, "INVALID_VERIFICATION_CODE", "인증 코드가 올바르지 않습니다."),
  VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "VERIFICATION_CODE_EXPIRED", "인증 코드가 만료되었습니다."),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),

  // Token
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),
  TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "만료된 토큰입니다."),
  REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_NOT_FOUND", "토큰을 찾을 수 없습니다."),

  // Quiz
  QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "QUIZ_NOT_FOUND", "퀴즈를 찾을 수 없습니다."),
  QUIZ_FORBIDDEN(HttpStatus.FORBIDDEN, "QUIZ_FORBIDDEN", "해당 퀴즈에 대한 권한이 없습니다."),
  INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", "지원하지 않는 카테고리입니다."),

  // Question
  QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "문제를 찾을 수 없습니다."),

  // Comment
  COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."),
  COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "COMMENT_FORBIDDEN", "해당 댓글에 대한 권한이 없습니다."),
  INVALID_COMMENT_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_COMMENT_FORMAT", "댓글 형식이 올바르지 않습니다."),

  // Notice
  NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTICE_NOT_FOUND", "공지를 찾을 수 없습니다."),

  // Notification
  NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."),

  // Upload
  INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "지원하지 않는 파일 타입입니다."),
  INVALID_FILE_SIZE(HttpStatus.BAD_REQUEST, "INVALID_FILE_SIZE", "허용된 파일 크기를 초과했습니다."),
  UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "업로드 요청을 찾을 수 없습니다."),
  UPLOAD_FORBIDDEN(HttpStatus.FORBIDDEN, "UPLOAD_FORBIDDEN", "해당 업로드에 접근할 권한이 없습니다."),
  UPLOAD_VERIFICATION_FAILED(
      HttpStatus.UNPROCESSABLE_ENTITY, "UPLOAD_VERIFICATION_FAILED", "S3 업로드 검증에 실패했습니다."),
  INVALID_UPLOAD_KEY(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD_KEY", "유효하지 않은 업로드 key 입니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;
}
