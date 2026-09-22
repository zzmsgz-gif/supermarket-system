package com.example.supermarket.repository;

import com.example.supermarket.entity.Announcement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long>, JpaSpecificationExecutor<Announcement> {

    List<Announcement> findByEnabledAndDeletedOrderBySortOrderAscPublishTimeDesc(Byte enabled, Byte deleted);

    List<Announcement> findByDeletedOrderBySortOrderAscPublishTimeDesc(Byte deleted);

    /** 系统托管的「会员日」公告（标题以此开头的那条，见 MemberDayService.syncAnnouncement）。 */
    Optional<Announcement> findFirstByDeletedAndTitleStartingWith(Byte deleted, String prefix);
}
