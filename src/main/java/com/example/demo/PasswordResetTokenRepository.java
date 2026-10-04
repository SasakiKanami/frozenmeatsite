package com.example.demo;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT token FROM PasswordResetToken token WHERE token.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    void deleteByUserIdAndConsumedAtIsNull(Integer userId);
}
