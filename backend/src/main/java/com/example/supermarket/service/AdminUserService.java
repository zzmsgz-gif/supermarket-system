package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.AdminUserResponse;
import com.example.supermarket.dto.AdminUserUpdateRequest;
import com.example.supermarket.dto.UserRoleRequest;
import com.example.supermarket.dto.UserStatusRequest;
import com.example.supermarket.entity.SysUser;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.SysUserRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminUserService {

    private static final byte ENABLED = 1;
    private static final byte DISABLED = 0;
    private static final byte NOT_DELETED = 0;
    private static final byte DELETED = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN");

    private final SysUserRepository userRepository;

    public AdminUserService(SysUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(int page, int size, String keyword, String role, Byte status) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SysUser> users = userRepository.findAll(buildUserSpec(keyword, role, status), pageable);
        List<AdminUserResponse> items = users.getContent().stream()
                .map(AdminUserResponse::from)
                .toList();
        return PageResponse.of(items, safePage, safeSize, users.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long id) {
        return AdminUserResponse.from(getActiveUser(id));
    }

    @Transactional
    public AdminUserResponse updateUser(Long id, AdminUserUpdateRequest request) {
        SysUser user = getActiveUser(id);
        String phone = normalizeBlank(request.getPhone());
        String email = normalizeBlank(request.getEmail());
        if (phone != null && userRepository.existsByPhoneAndIdNot(phone, id)) {
            throw new BusinessException(409, "Phone already exists");
        }
        if (email != null && userRepository.existsByEmailAndIdNot(email, id)) {
            throw new BusinessException(409, "Email already exists");
        }
        user.setNickname(defaultNickname(user.getUsername(), request.getNickname()));
        user.setPhone(phone);
        user.setEmail(email);
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        return AdminUserResponse.from(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse updateStatus(Long id, Long operatorId, UserStatusRequest request) {
        if (id.equals(operatorId) && Byte.valueOf(DISABLED).equals(request.getStatus())) {
            throw new BusinessException(409, "Current admin cannot disable itself");
        }
        validateStatus(request.getStatus());
        SysUser user = getActiveUser(id);
        user.setStatus(request.getStatus());
        return AdminUserResponse.from(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse updateRole(Long id, Long operatorId, UserRoleRequest request) {
        String role = normalizeRole(request.getRole());
        if (id.equals(operatorId) && !"ADMIN".equals(role)) {
            throw new BusinessException(409, "Current admin cannot remove its own admin role");
        }
        SysUser user = getActiveUser(id);
        user.setRole(role);
        return AdminUserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id, Long operatorId) {
        if (id.equals(operatorId)) {
            throw new BusinessException(409, "Current admin cannot delete itself");
        }
        SysUser user = getActiveUser(id);
        user.setStatus(DISABLED);
        user.setDeleted(DELETED);
        userRepository.save(user);
    }

    private SysUser getActiveUser(Long id) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getDeleted() == null || user.getDeleted().byteValue() != NOT_DELETED) {
            throw new ResourceNotFoundException("User not found");
        }
        return user;
    }

    private Specification<SysUser> buildUserSpec(String keyword, String role, Byte status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deleted"), NOT_DELETED));
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("username"), like),
                        cb.like(root.get("nickname"), like),
                        cb.like(root.get("phone"), like),
                        cb.like(root.get("email"), like)
                ));
            }
            if (StringUtils.hasText(role)) {
                predicates.add(cb.equal(root.get("role"), normalizeRole(role)));
            }
            if (status != null) {
                validateStatus(status);
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void validateStatus(Byte status) {
        if (!Byte.valueOf(ENABLED).equals(status) && !Byte.valueOf(DISABLED).equals(status)) {
            throw new BusinessException(400, "Invalid user status");
        }
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new BusinessException(400, "Role is required");
        }
        String normalized = role.trim().toUpperCase();
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new BusinessException(400, "Invalid user role");
        }
        return normalized;
    }

    private String defaultNickname(String username, String nickname) {
        return StringUtils.hasText(nickname) ? nickname.trim() : username;
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
