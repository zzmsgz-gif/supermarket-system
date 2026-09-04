package com.example.supermarket.repository;

import com.example.supermarket.entity.OrderEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {

    Optional<OrderEntity> findByIdAndUserId(Long id, Long userId);

    List<OrderEntity> findByStatusAndCreatedAtBefore(String status, LocalDateTime time);
}
