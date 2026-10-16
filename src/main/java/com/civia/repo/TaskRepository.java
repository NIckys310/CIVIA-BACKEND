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

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<Task> findByAssigneeIdAndStatusNotOrderByDueDateAsc(UUID userId, Enums.TaskStatus status);

    long countByProjectIdAndStatusNot(UUID projectId, Enums.TaskStatus status);

    long countByProjectIdAndStatusNotAndDueDateBefore(UUID projectId, Enums.TaskStatus status, LocalDate date);
}
