package com.pasadita.api.repositories;

import com.pasadita.api.entities.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Locks only the token row (no join to the employee, so it never contends with employee updates).
    // Serializes concurrent refreshes and logout of the same token
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    boolean existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(String familyId, LocalDateTime now);

    // "revokedAt IS NULL" keeps the original rotation time of already revoked rows, which the grace window measures
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.employee.id = :employeeId AND t.revokedAt IS NULL")
    int revokeAllActiveByEmployeeId(@Param("employeeId") Long employeeId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff OR t.revokedAt < :cutoff")
    int deleteExpiredOrRevokedBefore(@Param("cutoff") LocalDateTime cutoff);
}
