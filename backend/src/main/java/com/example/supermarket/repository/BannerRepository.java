package com.example.supermarket.repository;

import com.example.supermarket.entity.Banner;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    List<Banner> findByEnabledAndDeletedOrderBySortOrderAscIdAsc(Byte enabled, Byte deleted);

    List<Banner> findByDeletedOrderBySortOrderAscIdAsc(Byte deleted);
}
