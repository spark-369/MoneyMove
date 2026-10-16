package com.pstu.backend.repository;

import com.pstu.backend.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Query("select a.user from Account a where a.id = :accountId")
    Optional<User> findByAccountId(@Param("accountId") Long accountId);

    @Query("""
            select u from User u
            where lower(u.username) like :pattern
               or lower(u.fullName) like :pattern
            order by u.username asc
            """)
    List<User> search(@Param("pattern") String pattern, Pageable pageable);
}
