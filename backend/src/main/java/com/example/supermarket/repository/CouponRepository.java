package com.example.supermarket.repository;

import com.example.supermarket.entity.Coupon;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long>, JpaSpecificationExecutor<Coupon> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id and c.deleted = 0")
    Optional<Coupon> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Coupon c set c.receivedCount = c.receivedCount + 1"
            + " where c.id = :id and c.status = 1 and c.deleted = 0"
            + " and (c.totalCount = 0 or c.receivedCount < c.totalCount)")
    int increaseReceivedCount(@Param("id") Long id);
}
