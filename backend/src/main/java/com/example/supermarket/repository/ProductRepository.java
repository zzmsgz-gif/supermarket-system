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

    Optional<Product> findByIdAndDeleted(Long id, Byte deleted);

    List<Product> findByIdInAndDeleted(java.util.Collection<Long> ids, Byte deleted);

    boolean existsBySkuAndDeleted(String sku, Byte deleted);

    boolean existsBySkuAndDeletedAndIdNot(String sku, Byte deleted, Long id);

    boolean existsByCategoryIdAndDeleted(Long categoryId, Byte deleted);

    List<Product> findByStatusAndDeletedAndIsHot(String status, Byte deleted, Byte isHot, Pageable pageable);

    List<Product> findByStatusAndDeletedAndIsNew(String status, Byte deleted, Byte isNew, Pageable pageable);

    List<Product> findByStatusAndDeletedAndCategoryIdAndIdNot(String status, Byte deleted, Long categoryId, Long id, Pageable pageable);

    List<Product> findByStatusAndDeletedAndCategoryIdAndIdNotAndKind(
            String status, Byte deleted, Long categoryId, Long id, String kind, Pageable pageable);

    List<Product> findByStatusAndDeleted(String status, Byte deleted, Pageable pageable);

    List<Product> findByStatusAndDeletedAndKind(String status, Byte deleted, String kind, Pageable pageable);

    // ---------- 排除「秒杀独立商品」的统一口径查询 ----------
    // 秒杀独立商品（kind=FLASH）只在秒杀专区出现，绝不混入首页推荐/楼层/在看榜。
    // ⚠️ 口径与商品搜索保持一致：**kind 为空也算普通商品** —— 生产存在未回填 kind 的历史商品，
    //    直接 equal('NORMAL') 会把它们一并排除掉（首页凭空少一批商品）。
    // ⚠️ 同理不能用 `kind <> 'FLASH'`：SQL 里 NULL <> 'FLASH' 求值为 NULL（非真），NULL 行会被排除。

    /** 热门商品（排除 kind=FLASH），按销量降序 */
    @Query("select p from Product p where p.status = :status and p.deleted = :deleted "
            + "and p.isHot = :isHot and (p.kind is null or p.kind = :normalKind)")
    List<Product> findHotExcludeFlash(@Param("status") String status, @Param("deleted") Byte deleted,
                                      @Param("isHot") Byte isHot, @Param("normalKind") String normalKind,
                                      Pageable pageable);

    /** 新品（排除 kind=FLASH），按创建时间降序 */
    @Query("select p from Product p where p.status = :status and p.deleted = :deleted "
            + "and p.isNew = :isNew and (p.kind is null or p.kind = :normalKind)")
    List<Product> findNewExcludeFlash(@Param("status") String status, @Param("deleted") Byte deleted,
                                      @Param("isNew") Byte isNew, @Param("normalKind") String normalKind,
                                      Pageable pageable);

    /** 普通在售商品池（排除 kind=FLASH），供「猜你喜欢」候选使用 */
    @Query("select p from Product p where p.status = :status and p.deleted = :deleted "
            + "and (p.kind is null or p.kind = :normalKind)")
    List<Product> findNormalGoods(@Param("status") String status, @Param("deleted") Byte deleted,
                                  @Param("normalKind") String normalKind, Pageable pageable);

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

    /* ===== 数据统计聚合 ===== */

    @Query("select count(p) from Product p where p.deleted = 0 and p.status = 'ON_SALE'")
    long countOnSaleProducts();

    @Query("select coalesce(sum(p.stock), 0) from Product p where p.deleted = 0 and p.status = 'ON_SALE'")
    long sumOnSaleStock();
}
