package com.mehedi.waylo.repository;

import com.mehedi.waylo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.id = :id")
    java.util.Optional<User> lockById(@org.springframework.data.repository.query.Param("id") Long id);


    @org.springframework.data.jpa.repository.Query("""
        SELECT u FROM User u WHERE u.enabled = true
        AND LOWER(u.fullName) LIKE LOWER(CONCAT('%', :name, '%'))
        ORDER BY u.fullName
        """)
    java.util.List<User> searchPublicNames(
        @org.springframework.data.repository.query.Param("name") String name,
        org.springframework.data.domain.Pageable page);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}