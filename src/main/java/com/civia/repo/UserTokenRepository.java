package com.civia.repo;

import com.civia.domain.UserToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

    Optional<UserToken> findByTokenHashAndPurpose(String tokenHash, String purpose);

    long countByUserIdAndPurposeAndCreatedAtAfter(UUID userId, String purpose, Instant since);

    /** Marca el token como usado solo si nadie lo usó antes (evita la doble redención concurrente). */
    @Modifying(flushAutomatically = true)
    @Query("update UserToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int consume(@Param("id") UUID id, @Param("now") Instant now);

    /** Invalida los enlaces pendientes del usuario para ese propósito. */
    @Modifying(flushAutomatically = true)
    @Query("update UserToken t set t.usedAt = :now where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateAll(@Param("userId") UUID userId, @Param("purpose") String purpose, @Param("now") Instant now);
}
