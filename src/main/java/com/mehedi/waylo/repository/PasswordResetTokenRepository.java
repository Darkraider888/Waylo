package com.mehedi.waylo.repository;

import com.mehedi.waylo.entity.PasswordResetToken;
import com.mehedi.waylo.entity.User;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(
            String tokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT t FROM PasswordResetToken t
           WHERE t.tokenHash = :tokenHash
           """)
    Optional<PasswordResetToken> findForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying
    @Query("""
           UPDATE PasswordResetToken t
           SET t.used = true
           WHERE t.user = :user
           AND t.used = false
           """)
    int invalidateTokensForUser(
            @Param("user") User user
    );
}