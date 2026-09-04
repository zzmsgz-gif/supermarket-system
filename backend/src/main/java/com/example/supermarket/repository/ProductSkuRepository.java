package com.example.supermarket.repository;

import com.example.supermarket.entity.ProductSku;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {

    List<ProductSku> findByProductIdAndDeletedOrderBySortNoAscIdAsc(Long productId, Byte deleted);

    @Modifying
    @Query("delete from ProductSku s where s.productId = :productId and s.deleted = :deleted")
    void deleteByProductIdAndDeleted(@Param("productId") Long productId, @Param("deleted") Byte deleted);
}
