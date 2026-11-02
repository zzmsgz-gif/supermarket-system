package com.example.supermarket.repository;

import com.example.supermarket.entity.OrderEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {

    Optional<OrderEntity> findByIdAndUserId(Long id, Long userId);

    List<OrderEntity> findByStatusAndCreatedAtBefore(String status, LocalDateTime time);

    /**
     * 【订单状态 CAS 闸门】只有当前状态仍在 {@code fromStatuses} 里，才改成 {@code toStatus}（返回 1）。
     *
     * <p>为什么必须有这道闸门：支付、用户取消、超时关单、后台取消、后台退款这五条路径，
     * 每一条都会「还钱 / 还库存 / 还秒杀名额」。改动前它们各自是「先查状态、再做动作」，
     * 并发下两笔都能读到 PENDING_PAYMENT 并各自往下走 —— 于是同一张订单的库存被回补两次、
     * 秒杀名额被释放两次、钱包被退两次。库存虚高就是用户看到的「超卖」。
     * 加了 CAS 后，同一张订单只有抢到状态迁移的那一条路径能继续，其余必然返回 0 被拒。
     *
     * <p>⚠️ 用法要求：CAS 必须是事务里对这个订单的**第一个写操作**（在任何扣款/回库之前），
     * 且因为带 {@code clearAutomatically}，CAS 之后旧的实体已 detached，
     * 必须重新 {@code findById} 再改其它字段，否则 save 会把旧状态写回去。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderEntity o set o.status = :toStatus where o.id = :id and o.status in :fromStatuses")
    int casStatus(
            @Param("id") Long id,
            @Param("fromStatuses") Collection<String> fromStatuses,
            @Param("toStatus") String toStatus);

    /**
     * 【售后状态 CAS 闸门】同上，作用于 {@code refund_status}。
     * 防止同一笔退款申请被审批两次（双重退钱 + 双重回补库存/名额）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderEntity o set o.refundStatus = :toStatus where o.id = :id and o.refundStatus in :fromStatuses")
    int casRefundStatus(
            @Param("id") Long id,
            @Param("fromStatuses") Collection<String> fromStatuses,
            @Param("toStatus") String toStatus);

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
