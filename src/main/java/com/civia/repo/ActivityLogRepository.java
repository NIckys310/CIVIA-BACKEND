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

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    List<ActivityLog> findTop100ByProjectIdOrderByCreatedAtDesc(UUID projectId);

    @Query("select a from ActivityLog a where a.project.id in (select m.project.id from ProjectMember m where m.user.id = :userId) order by a.createdAt desc limit 30")
    List<ActivityLog> recentFor(@Param("userId") UUID userId);
}
