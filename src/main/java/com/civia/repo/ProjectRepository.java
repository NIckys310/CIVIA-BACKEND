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

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("select p from Project p where p.id in (select m.project.id from ProjectMember m where m.user.id = :userId) order by p.updatedAt desc")
    List<Project> findAccessible(@Param("userId") UUID userId);
}
