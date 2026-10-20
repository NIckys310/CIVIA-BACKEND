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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    List<AiConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    /** Fijadas primero; luego por el último mensaje. */
    @Query("select c from AiConversation c where c.user.id = :userId "
            + "order by c.pinned desc, coalesce(c.lastMessageAt, c.updatedAt) desc")
    List<AiConversation> listForUser(@Param("userId") UUID userId);
}
