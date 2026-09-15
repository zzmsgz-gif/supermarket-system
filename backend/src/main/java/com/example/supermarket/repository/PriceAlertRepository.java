package com.example.supermarket.repository;

import com.example.supermarket.entity.PriceAlert;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    Optional<PriceAlert> findByUserIdAndProductId(Long userId, Long productId);

    Page<PriceAlert> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndIsRead(Long userId, Byte isRead);

    List<PriceAlert> findByUserIdAndIsRead(Long userId, Byte isRead);
}
