package com.example.supermarket.repository;

import com.example.supermarket.entity.ProductReview;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ProductReviewRepository
        extends JpaRepository<ProductReview, Long>, JpaSpecificationExecutor<ProductReview> {

    List<ProductReview> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    boolean existsByOrderItemId(Long orderItemId);

    /**
     * 商品详情页的评价列表：**只取未隐藏的**。
     *
     * <p>⚠️ 与 {@link #findByOrderId}（订单维度）刻意不同：订单维度不过滤 hidden，因为前端
     * 「已评价」标记是从「该订单有没有评价」推出来的 —— 若那里也过滤，被隐藏的评价会让
     * 「评价」按钮重新出现，用户一点又拿到 409「已评价」，自相矛盾。
     */
    Page<ProductReview> findByProductIdAndHiddenOrderByCreatedAtDesc(Long productId, Byte hidden, Pageable pageable);

    /**
     * 星级聚合：[productId, 平均分, 评价数]。
     * ⚠️ **必须与列表同口径排除 hidden=0** —— 否则会出现"列表里看不到、平均分却被它拉低"的打架
     * （前端 ratingSummaryMap 与详情页列表同源）。
     */
    @Query("select r.productId, avg(r.rating), count(r) from ProductReview r where r.hidden = 0 group by r.productId")
    List<Object[]> aggregateRatingByProduct();

    /** 各星级评价数：[rating, count]，供后台评价管理汇总（含已隐藏，商家要看到全貌） */
    @Query("select r.rating, count(r) from ProductReview r group by r.rating order by r.rating desc")
    List<Object[]> countByRating();

    /** 平均分（含已隐藏，与 countByRating 同口径） */
    @Query("select avg(r.rating) from ProductReview r")
    Double averageRating();
}
