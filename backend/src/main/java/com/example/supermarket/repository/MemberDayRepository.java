package com.example.supermarket.repository;

import com.example.supermarket.entity.MemberDay;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberDayRepository extends JpaRepository<MemberDay, Long> {

    List<MemberDay> findAllByOrderByMemberDateAsc();

    List<MemberDay> findByEnabledOrderByMemberDateAsc(Byte enabled);

    /** 只取「今天及以后」的启用项：已过去的日期挂在列表里没意义（前台也不用展示） */
    List<MemberDay> findByEnabledAndMemberDateGreaterThanEqualOrderByMemberDateAsc(Byte enabled, LocalDate from);

    /** 同一天只允许配一条（服务层据此报 400）。 */
    Optional<MemberDay> findByMemberDate(LocalDate memberDate);

}
