package com.ongodmatchu.domain.user.entity;

/** 사용자 역할. 상위 역할은 하위 역할의 권한을 모두 포함한다 (USER < ADMIN < OWNER). */
public enum Role {
  USER(0),
  ADMIN(1),
  OWNER(2);

  private final int level;

  Role(int level) {
    this.level = level;
  }

  /** this 역할이 other 역할 이상인가 (상위 역할이 하위 권한을 포함). */
  public boolean isAtLeast(Role other) {
    return this.level >= other.level;
  }
}
