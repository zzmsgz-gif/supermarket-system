package com.example.supermarket.repository;

import com.example.supermarket.entity.ProductCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findByStatusAndDeletedOrderBySortNoAscIdAsc(Byte status, Byte deleted);

    List<ProductCategory> findByDeletedOrderBySortNoAscIdAsc(Byte deleted);

    boolean existsByIdAndDeleted(Long id, Byte deleted);

    boolean existsByParentIdAndDeleted(Long parentId, Byte deleted);

    boolean existsByNameAndParentIdAndDeleted(String name, Long parentId, Byte deleted);

    boolean existsByNameAndParentIdAndDeletedAndIdNot(String name, Long parentId, Byte deleted, Long id);
}
