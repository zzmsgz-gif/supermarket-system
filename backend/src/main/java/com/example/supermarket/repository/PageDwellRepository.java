package com.example.supermarket.repository;

import com.example.supermarket.entity.PageDwell;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PageDwellRepository extends JpaRepository<PageDwell, Long> {

    List<PageDwell> findByUserIdOrderByIdDesc(Long userId);

    // 「大家都在看」停留榜：排除秒杀独立商品（kind=FLASH）。
    // 2026-10-15 修：这条原生 SQL 原先对商品类型没有任何约束，谁被浏览得多谁上榜 ——
    //    已经结束的秒杀商品照样稳居榜首（用户实测：苹果秒杀结束后长期占着「大家都在看」）。
    // ⚠️ 口径与 ProductRepository 那几个 @Query 保持一致：kind 为空也算普通商品。
    @Query(value = "SELECT d.product_id, p.name, COUNT(*), ROUND(AVG(d.seconds), 1), p.cover_url, "
            + "p.price, p.original_price, p.member_price, p.stock, p.unit "
            + "FROM page_dwell d LEFT JOIN product p ON p.id = d.product_id "
            + "WHERE (p.kind IS NULL OR p.kind = 'NORMAL') "
            + "GROUP BY d.product_id, p.name, p.cover_url, p.price, p.original_price, p.member_price, p.stock, p.unit "
            + "ORDER BY COUNT(*) DESC LIMIT ?1", nativeQuery = true)
    List<Object[]> findDwellRankRaw(int limit);
}
