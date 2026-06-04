package com.ongodmatchu.domain.admin.service;

import com.ongodmatchu.domain.admin.dto.AdminUserResponse;
import com.ongodmatchu.domain.user.entity.Role;
import com.ongodmatchu.domain.user.entity.User;
import com.ongodmatchu.domain.user.repository.UserRepository;
import com.ongodmatchu.global.exception.BusinessException;
import com.ongodmatchu.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

  private static final int MAX_PAGE_SIZE = 50;

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public Page<AdminUserResponse> getUsers(
      String status, Role role, String query, Pageable pageable) {
    String normalizedQuery = (query == null || query.isBlank()) ? null : query.trim();
    Sort sort =
        pageable.getSort().isSorted()
            ? pageable.getSort()
            : Sort.by(Sort.Direction.DESC, "createdAt");
    Pageable capped =
        PageRequest.of(
            pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), sort);
    return userRepository
        .searchForAdmin(status, role, normalizedQuery, LocalDateTime.now(), capped)
        .map(AdminUserResponse::from);
  }

  @Transactional
  public AdminUserResponse suspend(Long actorId, UUID targetPublicId, int days) {
    User actor = getById(actorId);
    User target = getByPublicId(targetPublicId);
    AdminAuthorization.assertCanSuspend(actor, target);
    target.suspendUntil(LocalDateTime.now().plusDays(days));
    return AdminUserResponse.from(target);
  }

  @Transactional
  public AdminUserResponse unsuspend(Long actorId, UUID targetPublicId) {
    User actor = getById(actorId);
    User target = getByPublicId(targetPublicId);
    AdminAuthorization.assertCanSuspend(actor, target);
    target.clearSuspension();
    return AdminUserResponse.from(target);
  }

  @Transactional
  public AdminUserResponse changeRole(Long actorId, UUID targetPublicId, Role newRole) {
    User actor = getById(actorId);
    User target = getByPublicId(targetPublicId);
    AdminAuthorization.assertCanChangeRole(actor, target, newRole);
    target.changeRole(newRole);
    return AdminUserResponse.from(target);
  }

  private User getById(Long id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }

  private User getByPublicId(UUID publicId) {
    return userRepository
        .findByPublicId(publicId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }
}
