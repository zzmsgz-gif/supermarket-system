package com.example.supermarket.repository;

import com.example.supermarket.entity.UserCoupon;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long>, JpaSpecificationExecutor<UserCoupon> {

    Optional<UserCoupon> findByIdAndUserId(Long id, Long userId);

    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);

    /**
     * 每日可领券：判断「今天」是否已领过（2026-10-10）。
     *
     * <p>用 `received_at >= 当天 00:00` 判断，而不是存一个 claim_date 字段 ——
     * 时间范围查询不需要新增列，也不会出现「跨时区/补领」时字段与时间不一致的问题。
     *
     * <p>传的是**服务器本地时区**算出的当天零点，和写入 received_at 用的是同一个 Clock。
     */
    @Query("select count(uc) > 0 from UserCoupon uc where uc.userId = :userId and uc.couponId = :couponId "
            + "and uc.receivedAt >= :todayStart")
    boolean existsReceivedSince(@Param("userId") Long userId, @Param("couponId") Long couponId,
                                @Param("todayStart") java.time.LocalDateTime todayStart);

    List<UserCoupon> findByUserIdOrderByIdDesc(Long userId);

    Page<UserCoupon> findByUserId(Long userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select uc from UserCoupon uc where uc.id = :id and uc.userId = :userId")
    Optional<UserCoupon> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
