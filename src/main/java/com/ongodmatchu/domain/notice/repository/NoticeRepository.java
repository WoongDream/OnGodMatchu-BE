package com.ongodmatchu.domain.notice.repository;

import com.ongodmatchu.domain.notice.entity.Notice;
import com.ongodmatchu.domain.notice.entity.NoticeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

  /** 공개 목록 — 게시 상태만, 고정 우선 + 최신 게시순. */
  Page<Notice> findByStatusOrderByPinnedDescPublishedAtDesc(NoticeStatus status, Pageable pageable);

  /**
   * 백오피스 목록 — 상태/고정 필터 + 제목 검색(null bind 시 bytea 캐스팅 오류 회피로 CAST(:q AS string)). 고정 우선 + 작성 최신순 고정
   * 정렬.
   */
  @Query(
      """
      SELECT n FROM Notice n
      WHERE (:status IS NULL OR n.status = :status)
        AND (:pinned IS NULL OR n.pinned = :pinned)
        AND (CAST(:q AS string) IS NULL
           OR LOWER(n.title) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
      ORDER BY n.pinned DESC, n.createdAt DESC
      """)
  Page<Notice> searchForAdmin(
      @Param("status") NoticeStatus status,
      @Param("pinned") Boolean pinned,
      @Param("q") String q,
      Pageable pageable);

  long countByStatus(NoticeStatus status);

  long countByStatusAndPinned(NoticeStatus status, boolean pinned);
}
