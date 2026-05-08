package com.ongodmatchu.domain.user.repository;

import com.ongodmatchu.domain.user.entity.WithdrawalReasonRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalReasonRepository extends JpaRepository<WithdrawalReasonRecord, Long> {}
