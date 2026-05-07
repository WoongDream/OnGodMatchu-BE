package com.ongodmatchu.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentUpdateRequest(@NotBlank(message = "댓글 내용을 입력해주세요.") String content) {}
