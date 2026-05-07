package com.ongodmatchu.infra.s3;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Validated
@Tag(
    name = "Upload",
    description = "퀴즈 이미지 업로드 (presigned PUT) — 자세한 흐름은 docs/api-development.md#9-s3--파일-업로드-흐름")
public class UploadController {

  private final S3Service s3Service;

  @Operation(
      summary = "퀴즈 이미지 업로드 URL 발급 (presigned PUT)",
      description = "응답의 requiredHeaders 모든 값을 PUT 헤더에 부착해야 S3 가 허용. 한도 5MB / image/jpeg|png|webp")
  @PostMapping("/presigned")
  public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
      @Valid @RequestBody PresignedUrlRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    PresignedUrlResponse response =
        s3Service.generateUploadUrl(userDetails.getUser().getId(), request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @Operation(
      summary = "이미지 조회 URL 발급 (presigned GET)",
      description = "TTL 1시간. 응답마다 새 URL — 같은 객체 키여도 query string 다름. 비로그인 허용")
  @GetMapping("/signed")
  public ResponseEntity<ApiResponse<ViewUrlResponse>> getViewUrl(
      @RequestParam @NotBlank(message = "key 를 입력해주세요.") String key) {
    return ResponseEntity.ok(ApiResponse.ok(s3Service.generateViewUrl(key)));
  }

  @Operation(
      summary = "업로드 완료 (S3 검증)",
      description =
          "PUT 완료 후 호출. S3 HEAD 검증 + 태그 제거 + DB COMPLETED. 가능 에러: UPLOAD_NOT_FOUND(404), UPLOAD_FORBIDDEN(403), UPLOAD_VERIFICATION_FAILED(422), INVALID_FILE_TYPE/SIZE(400)")
  @PatchMapping("/complete")
  public ResponseEntity<ApiResponse<Void>> completeUpload(
      @Valid @RequestBody UploadCompleteRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    s3Service.completeUpload(userDetails.getUser().getId(), request.key());
    return ResponseEntity.ok(ApiResponse.ok());
  }
}
