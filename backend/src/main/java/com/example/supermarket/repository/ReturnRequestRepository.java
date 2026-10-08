package com.example.supermarket.repository;

import com.example.supermarket.entity.ReturnRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    /** 一笔订单当前的售后单（同一订单同时只允许一张未终结的售后单） */
    Optional<ReturnRequest> findByOrderIdAndStatusIn(Long orderId, List<String> statuses);

    /** 用户是否已有进行中的售后（防止重复申请） */
    @Query("select count(r) > 0 from ReturnRequest r where r.userId = :userId "
            + "and r.status in ('APPLYING','WAITING_SHIP','SHIPPED_BACK')")
    boolean hasOpenRequest(@Param("userId") Long userId);

    @Query("select r from ReturnRequest r where r.orderId = :orderId order by r.id desc")
    List<ReturnRequest> findByOrderId(@Param("orderId") Long orderId);

    @Query("select r from ReturnRequest r where r.userId = :userId order by r.id desc")
    List<ReturnRequest> findPageByUserId(@Param("userId") Long userId, Pageable pageable);

    long countByUserId(Long userId);

    /** 后台按状态筛选（售后管理用） */
    @Query("select r from ReturnRequest r where r.status = :status order by r.id desc")
    List<ReturnRequest> findByStatus(@Param("status") String status, Pageable pageable);

    long countByStatus(String status);

    @Query("select r from ReturnRequest r order by r.id desc")
    List<ReturnRequest> findAllPaged(Pageable pageable);

    long count();
}
