package com.ongodmatchu.domain.user.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * key = 보여주는(크롭) 프로필 이미지. originalKey = 크롭 전 원본(재편집용, 크롭 안 했으면 key 와 동일 또는 null). transform = FE 소유
 * opaque 크롭/변환 JSON (BE 미해석).
 */
public record ProfileImageUpdateRequest(
    @NotBlank(message = "key 를 입력해주세요.") String key, String originalKey, JsonNode transform) {}
