package com.example.supermarket.repository;

import com.example.supermarket.entity.ProductReview;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    List<ProductReview> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    boolean existsByOrderItemId(Long orderItemId);

    Page<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);
}
