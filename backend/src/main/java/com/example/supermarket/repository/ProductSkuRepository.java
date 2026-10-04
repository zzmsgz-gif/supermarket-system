package com.example.supermarket.repository;

import com.example.supermarket.entity.ProductSku;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {

    List<ProductSku> findByProductIdAndDeletedOrderBySortNoAscIdAsc(Long productId, Byte deleted);

    /** 批量取多商品的 SKU（购物车用，避免每行各查一次 SKU 表的 N+1） */
    List<ProductSku> findByProductIdInAndDeletedOrderBySortNoAscIdAsc(Collection<Long> productIds, Byte deleted);

    @Modifying
    @Query("delete from ProductSku s where s.productId = :productId and s.deleted = :deleted")
    void deleteByProductIdAndDeleted(@Param("productId") Long productId, @Param("deleted") Byte deleted);
}
