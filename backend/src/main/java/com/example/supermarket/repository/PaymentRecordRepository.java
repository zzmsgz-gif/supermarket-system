package com.example.supermarket.repository;

import com.example.supermarket.entity.PaymentRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    Optional<PaymentRecord> findByOrderId(Long orderId);

    /** 批量按订单 id 取支付记录（金额明细带渠道用，避免 N+1 / 全表扫） */
    List<PaymentRecord> findByOrderIdIn(Collection<Long> orderIds);
}
