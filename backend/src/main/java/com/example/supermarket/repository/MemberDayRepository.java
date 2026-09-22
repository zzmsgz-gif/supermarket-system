package com.example.supermarket.repository;

import com.example.supermarket.entity.MemberDay;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberDayRepository extends JpaRepository<MemberDay, Long> {

    List<MemberDay> findAllByOrderByDayOfMonthAsc();

    List<MemberDay> findByEnabledOrderByDayOfMonthAsc(Byte enabled);

    /** 同一「号」只允许配一条（服务层据此报 400，避免同一天两个倍率解释不清）。 */
    Optional<MemberDay> findByDayOfMonth(Integer dayOfMonth);

}
