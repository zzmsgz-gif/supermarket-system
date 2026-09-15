package com.example.supermarket.repository;

import com.example.supermarket.entity.PointLedger;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointLedgerRepository extends JpaRepository<PointLedger, Long> {

    Page<PointLedger> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    boolean existsByRefOrderIdAndType(Long refOrderId, String type);

    Optional<PointLedger> findByRefOrderIdAndType(Long refOrderId, String type);
}
