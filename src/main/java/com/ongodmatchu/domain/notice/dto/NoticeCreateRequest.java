package com.ongodmatchu.domain.notice.dto;

import com.ongodmatchu.domain.notice.entity.NoticeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공지 생성. status 미지정 시 DRAFT. */
public record NoticeCreateRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank String content,
    NoticeStatus status,
    boolean pinned) {}
