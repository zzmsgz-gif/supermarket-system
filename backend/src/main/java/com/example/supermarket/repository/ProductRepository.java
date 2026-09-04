package com.example.supermarket.repository;

import com.example.supermarket.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndStatusAndDeleted(Long id, String status, Byte deleted);

    boolean existsBySkuAndDeleted(String sku, Byte deleted);

    boolean existsBySkuAndDeletedAndIdNot(String sku, Byte deleted, Long id);

    boolean existsByCategoryIdAndDeleted(Long categoryId, Byte deleted);

    List<Product> findByStatusAndDeletedAndIsHot(String status, Byte deleted, Byte isHot, Pageable pageable);

    List<Product> findByStatusAndDeletedAndIsNew(String status, Byte deleted, Byte isNew, Pageable pageable);

    List<Product> findByStatusAndDeletedAndCategoryIdAndIdNot(String status, Byte deleted, Long categoryId, Long id, Pageable pageable);

    List<Product> findByStatusAndDeleted(String status, Byte deleted, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :quantity, p.sales = p.sales + :quantity where p.id = :id and p.status = :status and p.deleted = :deleted and p.stock >= :quantity")
    int deductStock(
            @Param("id") Long id,
            @Param("quantity") Integer quantity,
            @Param("status") String status,
            @Param("deleted") Byte deleted
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :quantity, p.sales = p.sales - :quantity where p.id = :id and p.sales >= :quantity")
    int returnStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :changeQuantity where p.id = :id and p.deleted = :deleted and p.stock + :changeQuantity >= 0")
    int adjustStock(@Param("id") Long id, @Param("changeQuantity") Integer changeQuantity, @Param("deleted") Byte deleted);

    @Query("select p from Product p where p.deleted = 0 and p.stock <= p.lowStockThreshold order by p.stock asc")
    java.util.List<Product> findLowStockProducts();
}
