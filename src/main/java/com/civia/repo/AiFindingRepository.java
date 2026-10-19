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

public interface AiFindingRepository extends JpaRepository<AiFinding, UUID> {

    List<AiFinding> findByAnalysisId(UUID analysisId);

    long countByAnalysisIdInAndStatusAndResolvedFalse(java.util.Collection<UUID> analysisIds, String status);
}
