package com.example.supermarket.repository;

import com.example.supermarket.entity.RechargeOrder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RechargeOrderRepository extends JpaRepository<RechargeOrder, Long> {

    List<RechargeOrder> findByUserIdAndId(Long userId, Long id);

    Optional<RechargeOrder> findByOrderNo(String orderNo);

    /** 拉取某个用户下、处于某状态、且已超过截止时间的订单（用于超时关闭） */
    List<RechargeOrder> findByUserIdAndStatusAndExpireAtBefore(Long userId, String status, LocalDateTime expireAt);

    /** 拉取所有超过截止时间仍未支付的订单（调度任务用，不限用户） */
    List<RechargeOrder> findByStatusAndExpireAtBefore(String status, LocalDateTime expireAt);
}
