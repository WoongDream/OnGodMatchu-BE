package com.ongodmatchu.domain.notice.dto;

import com.ongodmatchu.domain.notice.entity.NoticeStatus;
import jakarta.validation.constraints.Size;

/** 공지 수정 — null 필드는 미변경(부분 수정). */
public record NoticeUpdateRequest(
    @Size(max = 200) String title, String content, NoticeStatus status, Boolean pinned) {}
