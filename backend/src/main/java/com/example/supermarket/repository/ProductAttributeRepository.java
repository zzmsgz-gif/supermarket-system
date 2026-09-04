package com.example.supermarket.repository;

import com.example.supermarket.entity.ProductAttribute;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {

    List<ProductAttribute> findByProductIdAndDeletedOrderBySortNoAscIdAsc(Long productId, Byte deleted);

    @Modifying
    @Query("delete from ProductAttribute a where a.productId = :productId and a.deleted = :deleted")
    void deleteByProductIdAndDeleted(@Param("productId") Long productId, @Param("deleted") Byte deleted);
}
