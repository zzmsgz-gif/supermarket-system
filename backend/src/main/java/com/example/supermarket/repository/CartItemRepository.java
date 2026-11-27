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

    /**
     * 购物车行的展示顺序：**按加入时间**，最新的在最上面。
     *
     * <p>⚠️ 不要改回 {@code OrderByUpdatedAtDesc}。改数量会刷新 {@code updated_at}，
     * 那一行就会瞬间跳到列表最前面 —— 用户点几下「＋」，商品在眼前乱窜，手感极差
     * （2026-11-27 用户反馈「每次我添加一个商品的数量，添加几次它就跑上面去了」）。
     * 购物车的顺序应该只由「什么时候加进来的」决定，与之后怎么改数量无关。
     *
     * <p>{@code id DESC} 是兜底：同一秒内批量加购（如「再来一单」逐条 insert）时
     * created_at 精度只到秒，靠自增 id 保证顺序稳定、可复现。
     */
    List<CartItem> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

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

    /**
     * 清空某用户的购物车（一键清空）。
     *
     * <p>用一条 DELETE 而不是前端逐条删：商品多时逐条删要发 N 个请求，中途失败还会留下残行。
     * 清空这种「全有全无」的操作必须落在一次数据库语句里。
     */
    @Modifying
    @Query("delete from CartItem c where c.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

}
