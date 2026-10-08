package com.example.supermarket.repository;

import com.example.supermarket.entity.AmountRecord;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AmountRecordRepository extends JpaRepository<AmountRecord, Long> {

    /**
     * 分页查某用户的金额流水（倒序，新的在前）。
     *
     * <p>按 (user_id, created_at) 建了联合索引，正好匹配这里的 where + order by。
     */
    @Query("select r from AmountRecord r where r.userId = :userId order by r.id desc")
    List<AmountRecord> findPageByUserId(@Param("userId") Long userId, Pageable pageable);

    /** 总条数（分页用）。方法名必须以 count 开头，Spring Data 才能自动派生。 */
    long countByUserId(Long userId);

    /** 某订单的全部流水（订单详情页「金额构成」按时间线展示用） */
    List<AmountRecord> findByOrderIdOrderByIdAsc(Long orderId);

    /**
     * 某订单该类型是否已存在 —— 写流水前先查，避免重复记录。
     *
     * <p>不加唯一索引而靠应用层查：订单支付可能被重复调用（用户连点、网络重试），
     * 幂等检查必须在这里做；只靠唯一索引会直接抛异常把整个下单流程打断。
     */
    @Query("select count(r) > 0 from AmountRecord r where r.orderId = :orderId and r.type = :type")
    boolean existsByOrderIdAndType(@Param("orderId") Long orderId, @Param("type") String type);
}
