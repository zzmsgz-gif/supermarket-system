package com.example.supermarket.repository;

import com.example.supermarket.entity.CartItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    // 同一商品的不同规格(SKU)必须拆分成独立购物车行；skuSpec 为 null 时与 null 匹配。
    // 用 COALESCE 把 null 归一为空串，保证「无规格行」与「有规格行」互不覆盖。
    @Query("SELECT c FROM CartItem c WHERE c.userId = :userId AND c.productId = :productId " +
           "AND COALESCE(c.skuSpec, '') = COALESCE(:spec, '')")
    Optional<CartItem> findByUserIdAndProductIdAndSkuSpec(@Param("userId") Long userId,
                                                          @Param("productId") Long productId,
                                                          @Param("spec") String skuSpec);

    Optional<CartItem> findByIdAndUserId(Long id, Long userId);

    List<CartItem> findByUserIdAndIdIn(Long userId, Collection<Long> ids);

    List<CartItem> findByUserIdAndIdInAndSelected(Long userId, Collection<Long> ids, Byte selected);

    @Modifying
    @Query("delete from CartItem c where c.userId = :userId and c.id in :ids")
    int deleteByUserIdAndIds(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

}
