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

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    List<AiConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    /** Fijadas primero; luego por el último mensaje. */
    @Query("select c from AiConversation c where c.user.id = :userId "
            + "order by c.pinned desc, coalesce(c.lastMessageAt, c.updatedAt) desc")
    List<AiConversation> listForUser(@Param("userId") UUID userId);

    /** Búsqueda en el título y en el contenido de los mensajes. {@code pattern} ya viene en minúsculas con %. */
    @Query("select c from AiConversation c where c.user.id = :userId and (lower(c.title) like :pattern "
            + "or exists (select m.id from AiMessage m where m.conversation = c and lower(m.content) like :pattern)) "
            + "order by c.pinned desc, coalesce(c.lastMessageAt, c.updatedAt) desc")
    List<AiConversation> searchForUser(@Param("userId") UUID userId, @Param("pattern") String pattern);

    void deleteByUserId(UUID userId);
}
