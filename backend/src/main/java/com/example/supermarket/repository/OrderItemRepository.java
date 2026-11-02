package com.example.supermarket.repository;

import com.example.supermarket.entity.OrderItem;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);

    /** 批量取多订单的明细（订单列表分页用，避免 N+1：原每订单各查一次） */
    List<OrderItem> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);

    /**
     * 某用户在某秒杀场次里「已占用名额」的件数。
     * 只统计仍占着名额的订单（已取消/已关闭的单会回退名额，因此排除），用于校验每人限购。
     *
     * <p><b>必须传 {@code excludeOrderId} 排除当前正在创建的订单</b>：本方法是在订单行落库之后
     * 才调用的，不排除就会把当前这一单自己也算进去，导致首次下单就被判"已买满"。
     */
    @Query("select coalesce(sum(i.quantity), 0) from OrderItem i, OrderEntity o "
            + "where i.orderId = o.id and o.userId = :userId and i.flashSaleId = :flashSaleId "
            + "and o.id <> :excludeOrderId and o.status not in :excludedStatuses")
    long sumFlashQuantityByUser(
            @Param("userId") Long userId,
            @Param("flashSaleId") Long flashSaleId,
            @Param("excludeOrderId") Long excludeOrderId,
            @Param("excludedStatuses") Collection<String> excludedStatuses
    );

    /**
     * ⚠️ 2026-11-02 「每人限购并发绕过」的最终结论（别再走回头路）：
     *
     * <p>限购校验曾经试过加 {@code FOR UPDATE} 强制当前读 —— 能拦住同账号并发，但 REPEATABLE READ 下
     * FOR UPDATE 会加**间隙锁**，与其它事务 INSERT 订单互相等待 → MySQL 1213 死锁
     * （本机 3 次复现 2 次）。所以最终**不再加锁**，改为要求调用方事务处于
     * {@code READ_COMMITTED}（见 OrderService.createOrder/quickBuy/buildOrderFromItems）：
     * RC 下每条 SELECT 都读最新已提交版本，限购自然生效，且只加记录锁、不加间隙锁，不会死锁。
     *
     * <p>因此这里只保留上面那条普通聚合查询 {@link #sumFlashQuantityByUser}，
     * <b>如果哪天把它改成快照读场景（默认 RR 事务）调用，限购会静默失效。</b>
     */

    /** 某订单里占用了秒杀名额的订单行（取消/退款时回退用） */
    List<OrderItem> findByOrderIdAndFlashSaleIdIsNotNull(Long orderId);

    /**
     * 批量取「某用户在这些秒杀场次里各已占用多少件」，用于前台透出「我还能买几件」。
     * 返回 [flashSaleId(Long), 已占用件数(Long)]。
     *
     * <p>与 {@link #sumFlashQuantityByUser} 是两个查询、而不是加个 null 参数复用：那边要排除
     * {@code excludeOrderId}，而 JPQL 里 {@code o.id <> null} 恒为 UNKNOWN 会把所有行都过滤掉，
     * 传 null 会静默返回 0（＝"一件没买"），是个很难发现的坑。加购时订单还不存在，本就不需要排除。
     */
    @Query("select i.flashSaleId, sum(i.quantity) from OrderItem i, OrderEntity o "
            + "where i.orderId = o.id and o.userId = :userId and i.flashSaleId in :flashSaleIds "
            + "and o.status not in :excludedStatuses group by i.flashSaleId")
    List<Object[]> sumFlashQuantityByUserGrouped(
            @Param("userId") Long userId,
            @Param("flashSaleIds") Collection<Long> flashSaleIds,
            @Param("excludedStatuses") Collection<String> excludedStatuses
    );

    /**
     * 取「某用户的未付款订单在这些秒杀场次里占用了多少名额」，返回
     * [flashSaleId(Long), orderId(Long), orderNo(String), quantity(Integer)]，按 order id 升序。
     *
     * <p>用途：未付款订单同样占着名额（这是防超卖的正确做法），但用户能通过支付或取消订单自行释放，
     * 所以要把订单号告诉用户，给出「去支付 / 取消订单释放名额」的入口，而不是只报一句"已达限购"。
     * 升序保证调用方取到的第一笔就是最先超时的那笔。
     */
    @Query("select i.flashSaleId, o.id, o.orderNo, i.quantity from OrderItem i, OrderEntity o "
            + "where i.orderId = o.id and o.userId = :userId and i.flashSaleId in :flashSaleIds "
            + "and o.status = :unpaidStatus order by o.id asc")
    List<Object[]> findUnpaidFlashHolders(
            @Param("userId") Long userId,
            @Param("flashSaleIds") Collection<Long> flashSaleIds,
            @Param("unpaidStatus") String unpaidStatus
    );

    /* ===== 经营看板聚合 ===== */

    /**
     * 畅销商品排行：按销量倒序。
     * 返回 [productId(Long), productName(String), 销量(Long), 销售额(BigDecimal)]。
     * 只统计已支付订单，与看板 GMV 口径一致。
     */
    @Query("select i.productId, i.productName, sum(i.quantity), sum(i.subtotalAmount) "
            + "from OrderItem i, OrderEntity o "
            + "where i.orderId = o.id and o.status in :statuses "
            + "and o.createdAt >= :from and o.createdAt < :to "
            + "group by i.productId, i.productName order by sum(i.quantity) desc")
    List<Object[]> topProducts(
            @Param("statuses") Collection<String> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /** 品类销售占比：返回 [categoryId(Long), 销量(Long), 销售额(BigDecimal)] */
    @Query("select p.categoryId, sum(i.quantity), sum(i.subtotalAmount) "
            + "from OrderItem i, OrderEntity o, Product p "
            + "where i.orderId = o.id and i.productId = p.id and o.status in :statuses "
            + "and o.createdAt >= :from and o.createdAt < :to "
            + "group by p.categoryId order by sum(i.subtotalAmount) desc")
    List<Object[]> categoryShare(
            @Param("statuses") Collection<String> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** 秒杀成交：返回 [flashSaleId(Long), 销量(Long), 销售额(BigDecimal)]，不限时间窗（秒杀本身有档期） */
    @Query("select i.flashSaleId, sum(i.quantity), sum(i.subtotalAmount) "
            + "from OrderItem i, OrderEntity o "
            + "where i.orderId = o.id and i.flashSaleId is not null and o.status in :statuses "
            + "group by i.flashSaleId")
    List<Object[]> flashSaleSales(@Param("statuses") Collection<String> statuses);
}
