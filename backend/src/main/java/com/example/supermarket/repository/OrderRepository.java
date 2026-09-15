package com.example.supermarket.repository;

import com.example.supermarket.entity.OrderEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {

    Optional<OrderEntity> findByIdAndUserId(Long id, Long userId);

    List<OrderEntity> findByStatusAndCreatedAtBefore(String status, LocalDateTime time);

    /* ===== 数据统计聚合（仪表盘用，避免前端拿分页数据凑数字） ===== */

    @Query("select o.status, count(o) from OrderEntity o group by o.status")
    List<Object[]> countGroupByStatus();

    long countByStatus(String status);

    long countByCreatedAtAfter(LocalDateTime start);

    @Query("select coalesce(sum(o.payAmount), 0) from OrderEntity o where o.status in :statuses")
    BigDecimal sumPayAmountByStatusIn(@Param("statuses") Collection<String> statuses);

    @Query("select coalesce(sum(o.payAmount), 0) from OrderEntity o where o.status in :statuses and o.createdAt >= :start")
    BigDecimal sumPayAmountByStatusInAndCreatedAtAfter(@Param("statuses") Collection<String> statuses,
            @Param("start") LocalDateTime start);

    /* 近 7 天成交趋势：取时间窗内订单，由服务层按天聚合（订单量小，内存分组简单可靠） */
    List<OrderEntity> findByCreatedAtAfter(LocalDateTime start);

    long countByRefundStatus(String refundStatus);

    /* ===== 经营看板（按区间聚合，口径与仪表盘一致：用 createdAt，不用 paid_at） ===== */

    List<OrderEntity> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    @Query("select coalesce(sum(o.payAmount), 0) from OrderEntity o "
            + "where o.status in :statuses and o.createdAt >= :from and o.createdAt < :to")
    BigDecimal sumPayAmountByStatusInAndCreatedAtBetween(
            @Param("statuses") Collection<String> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("select count(o) from OrderEntity o "
            + "where o.status in :statuses and o.createdAt >= :from and o.createdAt < :to")
    long countByStatusInAndCreatedAtBetween(
            @Param("statuses") Collection<String> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** 区间内下过单的去重用户数（活跃买家） */
    @Query("select count(distinct o.userId) from OrderEntity o "
            + "where o.status in :statuses and o.createdAt >= :from and o.createdAt < :to")
    long countDistinctBuyersInRange(
            @Param("statuses") Collection<String> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** 区间内下单达到 minOrders 次的用户 id（复购用户） */
    @Query("select o.userId from OrderEntity o "
            + "where o.status in :statuses and o.createdAt >= :from and o.createdAt < :to "
            + "group by o.userId having count(o) >= :minOrders")
    List<Long> findRepeatBuyerIds(
            @Param("statuses") Collection<String> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("minOrders") long minOrders);

    long countByRefundStatusAndRefundedAtBetween(String refundStatus, LocalDateTime from, LocalDateTime to);

    @Query("select coalesce(sum(o.payAmount), 0) from OrderEntity o "
            + "where o.refundStatus = :refundStatus and o.refundedAt >= :from and o.refundedAt < :to")
    BigDecimal sumRefundedAmountInRange(
            @Param("refundStatus") String refundStatus,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
