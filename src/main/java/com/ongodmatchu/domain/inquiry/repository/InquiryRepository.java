package com.ongodmatchu.domain.inquiry.repository;

import com.ongodmatchu.domain.inquiry.entity.Inquiry;
import com.ongodmatchu.domain.inquiry.entity.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

  /** 본인 문의 목록 (최신순). */
  Page<Inquiry> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

  /**
   * 백오피스 목록 — 상태 필터 + 제목 검색(null bind 시 bytea 캐스팅 오류 회피로 CAST(:q AS string)). 접수 최신순. 작성자(user)
   * fetch join 으로 N+1 회피.
   */
  @Query(
      """
      SELECT i FROM Inquiry i
      JOIN FETCH i.user
      WHERE (:status IS NULL OR i.status = :status)
        AND (CAST(:q AS string) IS NULL
           OR LOWER(i.title) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
      ORDER BY i.createdAt DESC
      """)
  Page<Inquiry> searchForAdmin(
      @Param("status") InquiryStatus status, @Param("q") String q, Pageable pageable);

  long countByStatus(InquiryStatus status);
}
