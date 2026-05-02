package com.ongodmatchu.infra.s3;

import com.ongodmatchu.domain.auth.security.CustomUserDetails;
import com.ongodmatchu.global.response.ApiResponse;
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
public class UploadController {

  private final S3Service s3Service;

  @PostMapping("/presigned")
  public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
      @Valid @RequestBody PresignedUrlRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    PresignedUrlResponse response =
        s3Service.generateUploadUrl(userDetails.getUser().getId(), request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/signed")
  public ResponseEntity<ApiResponse<ViewUrlResponse>> getViewUrl(
      @RequestParam @NotBlank(message = "key 를 입력해주세요.") String key) {
    return ResponseEntity.ok(ApiResponse.ok(s3Service.generateViewUrl(key)));
  }

  @PatchMapping("/complete")
  public ResponseEntity<ApiResponse<Void>> completeUpload(
      @Valid @RequestBody UploadCompleteRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    s3Service.completeUpload(userDetails.getUser().getId(), request.key());
    return ResponseEntity.ok(ApiResponse.ok());
  }
}
