package com.example.supermarket.repository;

import com.example.supermarket.entity.UserMessage;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMessageRepository extends JpaRepository<UserMessage, Long> {

    Page<UserMessage> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<UserMessage> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type, Pageable pageable);

    long countByUserIdAndIsRead(Long userId, Byte isRead);

    List<UserMessage> findByUserIdAndIsRead(Long userId, Byte isRead);

    boolean existsByUserIdAndDedupeKey(Long userId, String dedupeKey);
}
