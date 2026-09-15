package com.example.supermarket.repository;

import com.example.supermarket.entity.LegalDoc;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalDocRepository extends JpaRepository<LegalDoc, Long> {

    Optional<LegalDoc> findByDocKey(String docKey);

    Optional<LegalDoc> findByDocKeyAndEnabled(String docKey, Byte enabled);

    List<LegalDoc> findByEnabledOrderBySortNoAscIdAsc(Byte enabled);

    List<LegalDoc> findAllByOrderBySortNoAscIdAsc();
}
