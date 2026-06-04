package com.ongodmatchu.domain.admin.repository;

import com.ongodmatchu.domain.admin.entity.AdminUserHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserHistoryRepository extends JpaRepository<AdminUserHistory, Long> {

  Page<AdminUserHistory> findByTargetUserIdOrderByCreatedAtDesc(
      Long targetUserId, Pageable pageable);
}
