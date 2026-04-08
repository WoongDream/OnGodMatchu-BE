package com.ongodmatchu.infra.s3;

import com.ongodmatchu.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

  private final S3Service s3Service;

  @PostMapping("/presigned")
  public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
      @Valid @RequestBody PresignedUrlRequest request) {
    PresignedUrlResponse response =
        s3Service.generatePresignedUrl(request.folder(), request.filename());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }
}
