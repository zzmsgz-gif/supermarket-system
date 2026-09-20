package com.example.supermarket.repository;

import com.example.supermarket.entity.HotSearch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotSearchRepository extends JpaRepository<HotSearch, Long> {

    /** 前台用：启用的、未删的，按排序值升序、同序按 id 升序（保证稳定） */
    List<HotSearch> findByEnabledAndDeletedOrderBySortOrderAscIdAsc(Byte enabled, Byte deleted);

    /** 后台用：全部未删的 */
    List<HotSearch> findByDeletedOrderBySortOrderAscIdAsc(Byte deleted);

    /** 查重：同关键词（排除指定 id，用于编辑时排除自己） */
    List<HotSearch> findByKeywordAndDeleted(String keyword, Byte deleted);
}
