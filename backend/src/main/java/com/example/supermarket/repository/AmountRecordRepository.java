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

    /**
     * 按类型分组分页查。
     *
     * <p><b>为什么筛选必须下推到 SQL 而不是前端本地 filter</b>：
     * 前端一次拉一页再本地过滤，翻页时「第 2 页」拿的是服务端的第 2 页、
     * 而不是「筛选结果的第 2 页」，页数和总数必然对不上（本项目商品列表踩过同一个坑）。
     */
    @Query("select r from AmountRecord r where r.userId = :userId and r.type in :types order by r.id desc")
    List<AmountRecord> findPageByUserIdAndTypes(@Param("userId") Long userId,
                                               @Param("types") List<String> types,
                                               Pageable pageable);

    @Query("select count(r) from AmountRecord r where r.userId = :userId and r.type in :types")
    long countByUserIdAndTypes(@Param("userId") Long userId, @Param("types") List<String> types);

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
