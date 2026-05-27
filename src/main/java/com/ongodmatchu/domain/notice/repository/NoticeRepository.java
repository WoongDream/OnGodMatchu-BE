package com.ongodmatchu.domain.notice.repository;

import com.ongodmatchu.domain.notice.entity.Notice;
import com.ongodmatchu.domain.notice.entity.NoticeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

  Page<Notice> findByTypeAndPublishedAtIsNotNull(NoticeType type, Pageable pageable);

  Optional<Notice> findByIdAndTypeAndPublishedAtIsNotNull(Long id, NoticeType type);
}
