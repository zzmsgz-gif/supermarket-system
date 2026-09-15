package com.example.supermarket.repository;

import com.example.supermarket.entity.FlashSale;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {

    /** 后台列表：按启用状态过滤（null 表示全部） */
    @Query("select f from FlashSale f where f.deleted = :deleted "
            + "and (:status is null or f.status = :status) order by f.sortNo asc, f.id desc")
    List<FlashSale> listForAdmin(@Param("deleted") Byte deleted, @Param("status") Byte status);

    /** 公开列表：当前正在进行的场次（在时间窗内、启用、还有名额） */
    @Query("select f from FlashSale f where f.deleted = :deleted and f.status = :status "
            + "and f.startTime <= :now and f.endTime > :now and f.soldQuota < f.totalQuota "
            + "order by f.sortNo asc, f.id asc")
    List<FlashSale> findRunning(
            @Param("deleted") Byte deleted,
            @Param("status") Byte status,
            @Param("now") LocalDateTime now
    );

    /** 即将开始（用于前台预告） */
    @Query("select f from FlashSale f where f.deleted = :deleted and f.status = :status "
            + "and f.startTime > :now order by f.startTime asc, f.id asc")
    List<FlashSale> findUpcoming(
            @Param("deleted") Byte deleted,
            @Param("status") Byte status,
            @Param("now") LocalDateTime now
    );

    /** 按商品批量取「此刻进行中」的场次，用于商品列表/详情透出秒杀价（避免 N+1） */
    @Query("select f from FlashSale f where f.deleted = :deleted and f.status = :status "
            + "and f.productId in :productIds "
            + "and f.startTime <= :now and f.endTime > :now and f.soldQuota < f.totalQuota")
    List<FlashSale> findRunningByProductIds(
            @Param("deleted") Byte deleted,
            @Param("status") Byte status,
            @Param("productIds") Collection<Long> productIds,
            @Param("now") LocalDateTime now
    );

    Optional<FlashSale> findByIdAndDeleted(Long id, Byte deleted);

    boolean existsByProductIdAndDeletedAndStatusAndEndTimeAfter(
            Long productId, Byte deleted, Byte status, LocalDateTime now);

    boolean existsByProductIdAndDeletedAndStatusAndEndTimeAfterAndIdNot(
            Long productId, Byte deleted, Byte status, LocalDateTime now, Long id);

    /**
     * 原子占用名额：只有剩余名额足够时才更新成功（返回 1）。
     * 并发抢购时靠这条带条件的 UPDATE 防超卖，不能改成先查后改。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FlashSale f set f.soldQuota = f.soldQuota + :quantity "
            + "where f.id = :id and f.soldQuota + :quantity <= f.totalQuota")
    int reserveQuota(@Param("id") Long id, @Param("quantity") Integer quantity);

    /** 回退名额（取消 / 超时关闭 / 退款） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FlashSale f set f.soldQuota = f.soldQuota - :quantity "
            + "where f.id = :id and f.soldQuota >= :quantity")
    int releaseQuota(@Param("id") Long id, @Param("quantity") Integer quantity);
}
