package com.civia.storage;

import com.civia.domain.StoredObject;
import com.civia.repo.StoredObjectRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Guarda los archivos subidos dentro de la base de datos. Pensado para el plan gratuito: Render borra
 * su disco en cada reinicio y así no hace falta un servicio de almacenamiento aparte.
 *
 * <p>Se activa con {@code civia.storage.db.enabled=true} (por defecto en producción) y solo si no hay un
 * bucket S3 configurado, que siempre tiene prioridad. Como la base gratuita es pequeña, limita el
 * tamaño de cada archivo y el espacio total que pueden ocupar.
 */
@Component
@ConditionalOnExpression("'${civia.storage.s3.bucket:}' == '' && '${civia.storage.db.enabled:false}' == 'true'")
public class DatabaseObjectStore implements ObjectStore {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseObjectStore.class);
    private static final long MB = 1024L * 1024;

    private final StoredObjectRepository objects;
    private final long maxFileBytes;
    private final long maxTotalBytes;

    public DatabaseObjectStore(StoredObjectRepository objects,
                               @Value("${civia.storage.db.max-file-mb:25}") long maxFileMb,
                               @Value("${civia.storage.db.max-total-mb:350}") long maxTotalMb) {
        this.objects = objects;
        this.maxFileBytes = maxFileMb * MB;
        this.maxTotalBytes = maxTotalMb * MB;
        LOG.info("Archivos guardados en la base de datos (máximo {} MB por archivo y {} MB en total)", maxFileMb, maxTotalMb);
    }

    @Override
    @Transactional
    public void put(String key, Path file, long size) {
        if (size > maxFileBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "El archivo supera el máximo de " + (maxFileBytes / MB) + " MB permitido en este plan.");
        }
        if (objects.totalBytes() + size > maxTotalBytes) {
            throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE,
                    "El almacenamiento de archivos está lleno. Borra archivos que ya no uses e intenta de nuevo.");
        }
        StoredObject o = new StoredObject();
        o.setKey(key);
        o.setSize(size);
        try {
            o.setData(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        objects.save(o);
    }

    @Override
    @Transactional(readOnly = true)
    public InputStream get(String key) {
        return objects.findById(key).map(o -> (InputStream) new ByteArrayInputStream(o.getData())).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public long size(String key) {
        return objects.sizeOf(key).orElse(-1L);
    }

    @Override
    @Transactional
    public void delete(String key) {
        objects.deleteById(key);
    }
}
