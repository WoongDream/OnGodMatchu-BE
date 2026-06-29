package com.ongodmatchu.domain.inquiry.dto;

import com.ongodmatchu.domain.user.dto.UserDisplay;
import com.ongodmatchu.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 문의한 사용자 표시 — 닉네임/프로필이미지(presigned)/publicId. 탈퇴 사용자는 마스킹(클릭→프로필 모달 비활성). */
public record InquiryAuthorResponse(
    @Schema(description = "탈퇴 사용자면 null", nullable = true) UUID publicId,
    String nickname,
    @Schema(description = "presigned URL. 탈퇴 사용자면 null", nullable = true) String profileImageUrl) {

  /** {@code resolvedImageUrl} 는 호출자가 presign 해 전달. 탈퇴 사용자는 UserDisplay 로 마스킹. */
  public static InquiryAuthorResponse of(User user, String resolvedImageUrl) {
    return new InquiryAuthorResponse(
        UserDisplay.publicIdOf(user),
        UserDisplay.nicknameOf(user),
        UserDisplay.profileImageUrlOf(user, resolvedImageUrl));
  }
}
