package com.example.supermarket.repository;

import com.example.supermarket.entity.UserFavorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    Optional<UserFavorite> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    Page<UserFavorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<UserFavorite> findByUserId(Long userId);

    List<UserFavorite> findByProductId(Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);
}
