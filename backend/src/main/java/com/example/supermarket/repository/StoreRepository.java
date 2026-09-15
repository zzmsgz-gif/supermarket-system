package com.example.supermarket.repository;

import com.example.supermarket.entity.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByDeletedAndStatusOrderBySortNoAscIdAsc(Byte deleted, Byte status);

    List<Store> findByDeletedOrderBySortNoAscIdAsc(Byte deleted);

    Optional<Store> findByIdAndDeleted(Long id, Byte deleted);

    boolean existsByNameAndDeleted(String name, Byte deleted);

    boolean existsByNameAndDeletedAndIdNot(String name, Byte deleted, Long id);
}
