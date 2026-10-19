package com.civia.repo;

import com.civia.domain.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<Document> findByProjectIdAndKindOrderByCreatedAtDesc(UUID projectId, Enums.DocumentKind kind);

    long countByProjectIdAndKind(UUID projectId, Enums.DocumentKind kind);

    @Query("select coalesce(sum(d.sizeBytes), 0) from Document d where d.project.id in (select m.project.id from ProjectMember m where m.user.id = :userId)")
    long storageUsedBy(@Param("userId") UUID userId);
}
