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

public interface LibraryFavoriteRepository extends JpaRepository<LibraryFavorite, UUID> {

    List<LibraryFavorite> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<LibraryFavorite> findByUserIdAndSlug(UUID userId, String slug);
}
