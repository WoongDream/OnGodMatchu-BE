package com.ongodmatchu.infra.s3;

import com.ongodmatchu.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "upload_meta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadMeta {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "s3_key", nullable = false, unique = true)
  private String s3Key;

  private String originalName;

  @Column(nullable = false)
  private String contentType;

  private Long sizeBytes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UploadStatus status;

  @CreationTimestamp
  @Column(updatable = false)
  private LocalDateTime createdAt;

  private LocalDateTime completedAt;

  @Builder
  private UploadMeta(
      User user, String s3Key, String originalName, String contentType, Long sizeBytes) {
    this.user = user;
    this.s3Key = s3Key;
    this.originalName = originalName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.status = UploadStatus.PENDING;
  }

  public void markCompleted(Long verifiedSizeBytes) {
    this.status = UploadStatus.COMPLETED;
    this.completedAt = LocalDateTime.now();
    if (verifiedSizeBytes != null) {
      this.sizeBytes = verifiedSizeBytes;
    }
  }
}
