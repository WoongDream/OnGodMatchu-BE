package com.ongodmatchu.domain.visit.entity;

import com.ongodmatchu.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "visit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "anon_id", nullable = false, length = 36)
  private String anonId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(nullable = false, length = 255)
  private String path;

  @Column(name = "visited_at", nullable = false, updatable = false)
  private LocalDateTime visitedAt;

  @Builder
  private VisitLog(String anonId, User user, String path) {
    this.anonId = anonId;
    this.user = user;
    this.path = path;
  }

  @PrePersist
  private void onCreate() {
    if (this.visitedAt == null) {
      this.visitedAt = LocalDateTime.now();
    }
  }
}
