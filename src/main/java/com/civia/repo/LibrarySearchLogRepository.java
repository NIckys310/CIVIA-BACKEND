package com.civia.repo;

import com.civia.domain.LibrarySearchLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibrarySearchLogRepository extends JpaRepository<LibrarySearchLog, UUID> {}
