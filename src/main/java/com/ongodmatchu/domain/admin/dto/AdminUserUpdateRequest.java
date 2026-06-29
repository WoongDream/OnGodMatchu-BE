package com.ongodmatchu.domain.admin.dto;

import com.ongodmatchu.domain.user.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;

/**
 * 유저 수정 화면의 "변경사항 저장". 변경할 항목만 채워 보낸다. notification 이 있으면 함께 발송(알림 보내고 수정 저장), 없으면 알림 없이 저장. 모든 변경은
 * 한 트랜잭션으로 적용되고 관리 이력 1행으로 기록된다.
 */
public record AdminUserUpdateRequest(
    @Schema(description = "변경할 역할. null 이면 미변경", nullable = true) Role role,
    @Valid @Schema(description = "정지 상태 변경. null 이면 미변경", nullable = true)
        SuspensionUpdate suspension,
    boolean resetProfileImage,
    boolean resetNickname,
    boolean resetBio,
    @Valid @Schema(description = "함께 보낼 알림. null 이면 알림 없이 저장", nullable = true)
        SendNotificationRequest notification) {

  public record SuspensionUpdate(
      @Schema(description = "true=정지, false=정지 해제") boolean suspend,
      @Schema(description = "정지 종료일. suspend=true 시 필수", nullable = true) OffsetDateTime until) {}
}
