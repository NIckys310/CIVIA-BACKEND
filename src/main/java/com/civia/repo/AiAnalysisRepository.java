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

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, UUID> {

    List<AiAnalysis> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<AiAnalysis> findFirstByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    @Query("select count(distinct a.document.id) from AiAnalysis a where a.project.id = :projectId and a.analysisType = 'PLAN'")
    long countAnalyzedPlans(@Param("projectId") UUID projectId);
}
