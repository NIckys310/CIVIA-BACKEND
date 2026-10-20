package com.civia.repo;

import com.civia.domain.LibrarySearchLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibrarySearchLogRepository extends JpaRepository<LibrarySearchLog, UUID> {

    List<LibrarySearchLog> findTop40ByUserIdOrderByCreatedAtDesc(UUID userId);
}
