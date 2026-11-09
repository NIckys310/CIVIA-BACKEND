package com.civia.repo;

import com.civia.domain.ProjectModel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectModelRepository extends JpaRepository<ProjectModel, UUID> {

    Optional<ProjectModel> findByProjectId(UUID projectId);
}
