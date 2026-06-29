package com.ongodmatchu.domain.admin.dto;

import com.ongodmatchu.domain.user.entity.Role;
import jakarta.validation.constraints.NotNull;

/** 권한 변경(임명/해임/자가 사임). OWNER 부여는 서비스 권한 경계에서 거부된다. */
public record ChangeRoleRequest(@NotNull Role role) {}
