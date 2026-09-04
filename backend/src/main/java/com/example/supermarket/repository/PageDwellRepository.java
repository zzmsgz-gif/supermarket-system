package com.example.supermarket.repository;

import com.example.supermarket.entity.PageDwell;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PageDwellRepository extends JpaRepository<PageDwell, Long> {

    List<PageDwell> findByUserIdOrderByIdDesc(Long userId);

    @Query(value = "SELECT d.product_id, p.name, COUNT(*), ROUND(AVG(d.seconds), 1), p.cover_url "
            + "FROM page_dwell d LEFT JOIN product p ON p.id = d.product_id "
            + "GROUP BY d.product_id, p.name, p.cover_url ORDER BY COUNT(*) DESC LIMIT ?1", nativeQuery = true)
    List<Object[]> findDwellRankRaw(int limit);
}
