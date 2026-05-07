package com.ongodmatchu.domain.comment.dto;

import com.ongodmatchu.domain.comment.entity.QuizComment;
import java.time.LocalDateTime;
import java.util.UUID;

public record CommentResponse(
    Long id,
    String content,
    UUID authorPublicId,
    String authorNickname,
    String authorProfileImageUrl,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static CommentResponse from(QuizComment comment, String authorProfileImageUrl) {
    return new CommentResponse(
        comment.getId(),
        comment.getContent(),
        comment.getUser().getPublicId(),
        comment.getUser().getNickname(),
        authorProfileImageUrl,
        comment.getCreatedAt(),
        comment.getUpdatedAt());
  }
}
