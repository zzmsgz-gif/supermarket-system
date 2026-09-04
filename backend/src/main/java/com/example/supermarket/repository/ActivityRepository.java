package com.example.supermarket.repository;

import com.example.supermarket.entity.Activity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ActivityRepository extends JpaRepository<Activity, Long>, JpaSpecificationExecutor<Activity> {

    List<Activity> findByStatusAndDeletedAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
            Byte status, Byte deleted, LocalDateTime start, LocalDateTime end);

    List<Activity> findByStatusAndDeleted(Byte status, Byte deleted, Pageable pageable);
}
