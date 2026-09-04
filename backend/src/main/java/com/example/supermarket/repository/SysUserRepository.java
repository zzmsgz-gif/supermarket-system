package com.example.supermarket.repository;

import com.example.supermarket.entity.SysUser;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysUserRepository extends JpaRepository<SysUser, Long>, JpaSpecificationExecutor<SysUser> {

    Optional<SysUser> findByUsernameAndDeleted(String username, Byte deleted);

    boolean existsByUsernameAndDeleted(String username, Byte deleted);

    boolean existsByPhoneAndDeleted(String phone, Byte deleted);

    boolean existsByEmailAndDeleted(String email, Byte deleted);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from SysUser u where u.id = :id and u.deleted = :deleted")
    Optional<SysUser> findByIdAndDeletedForUpdate(@Param("id") Long id, @Param("deleted") Byte deleted);
}
