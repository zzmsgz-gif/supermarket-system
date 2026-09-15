package com.example.supermarket.repository;

import com.example.supermarket.entity.ProductReview;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    List<ProductReview> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    boolean existsByOrderItemId(Long orderItemId);

    Page<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    /* 星级聚合：[productId, 平均分, 评价数]，供商品卡/详情页展示平均星级 */
    @Query("select r.productId, avg(r.rating), count(r) from ProductReview r group by r.productId")
    List<Object[]> aggregateRatingByProduct();
}
