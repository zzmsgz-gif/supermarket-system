package com.example.supermarket.repository;

import com.example.supermarket.entity.StockLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StockLogRepository extends JpaRepository<StockLog, Long>, JpaSpecificationExecutor<StockLog> {
}
