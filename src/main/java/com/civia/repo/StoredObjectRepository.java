package com.civia.repo;

import com.civia.domain.StoredObject;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredObjectRepository extends JpaRepository<StoredObject, String> {

    /** Tamaño sin cargar el contenido. */
    @Query("select o.size from StoredObject o where o.key = :key")
    Optional<Long> sizeOf(@Param("key") String key);

    /** Espacio ocupado por todos los archivos guardados en la base. */
    @Query("select coalesce(sum(o.size), 0) from StoredObject o")
    long totalBytes();
}
