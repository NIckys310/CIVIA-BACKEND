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

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findByDocumentIdOrderByVersionDesc(UUID documentId);

    @Query("select coalesce(sum(v.sizeBytes), 0) from DocumentVersion v where v.uploadedBy.id = :userId")
    long bytesUploadedBy(@Param("userId") UUID userId);
}
