package com.ongodmatchu.domain.user.entity;

import com.ongodmatchu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 익명 통계용 — user_id 컬럼은 두지 않는다. 주관식 텍스트만 저장하며 객관식 키 컬럼은 V19 에서 제거. */
@Entity
@Table(name = "withdrawal_reasons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawalReasonRecord extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "reason_text", length = 500)
  private String reasonText;

  @Builder
  private WithdrawalReasonRecord(String reasonText) {
    this.reasonText = reasonText;
  }
}
