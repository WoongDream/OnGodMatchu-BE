package com.ongodmatchu.domain.comment.dto;

import com.ongodmatchu.domain.comment.entity.QuizComment;
import com.ongodmatchu.domain.user.dto.UserDisplay;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.global.util.TimeFormat;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CommentResponse(
    Long id,
    String content,
    UUID authorPublicId,
    String authorNickname,
    String authorProfileImageUrl,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static CommentResponse from(QuizComment comment, String authorProfileImageUrl) {
    User author = comment.getUser();
    return new CommentResponse(
        comment.getId(),
        comment.getContent(),
        UserDisplay.publicIdOf(author),
        UserDisplay.nicknameOf(author),
        UserDisplay.profileImageUrlOf(author, authorProfileImageUrl),
        TimeFormat.toResponse(comment.getCreatedAt()),
        TimeFormat.toResponse(comment.getUpdatedAt()));
  }
}
