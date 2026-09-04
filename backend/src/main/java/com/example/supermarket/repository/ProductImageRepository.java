package com.example.supermarket.repository;

import com.example.supermarket.entity.ProductImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdAndDeletedOrderBySortNoAscIdAsc(Long productId, Byte deleted);

    @Modifying
    @Query("delete from ProductImage i where i.productId = :productId and i.deleted = :deleted")
    void deleteByProductIdAndDeleted(@Param("productId") Long productId, @Param("deleted") Byte deleted);
}
