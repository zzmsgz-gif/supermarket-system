package com.example.supermarket.repository;

import com.example.supermarket.entity.PasswordResetRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {

    Page<PasswordResetRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PasswordResetRequest> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /** 幂等判据：同一账号已有未处理申请时不再重复落库（防止刷单式提交把后台刷爆） */
    boolean existsByUserIdAndStatus(Long userId, String status);

    long countByStatus(String status);
}
