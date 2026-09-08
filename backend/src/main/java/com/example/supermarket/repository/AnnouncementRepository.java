package com.example.supermarket.repository;

import com.example.supermarket.entity.Announcement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long>, JpaSpecificationExecutor<Announcement> {

    List<Announcement> findByEnabledAndDeletedOrderBySortOrderAscPublishTimeDesc(Byte enabled, Byte deleted);
}
