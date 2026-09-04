package com.example.supermarket.repository;

import com.example.supermarket.entity.UserCoupon;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long>, JpaSpecificationExecutor<UserCoupon> {

    Optional<UserCoupon> findByIdAndUserId(Long id, Long userId);

    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);

    List<UserCoupon> findByUserIdOrderByIdDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select uc from UserCoupon uc where uc.id = :id and uc.userId = :userId")
    Optional<UserCoupon> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
