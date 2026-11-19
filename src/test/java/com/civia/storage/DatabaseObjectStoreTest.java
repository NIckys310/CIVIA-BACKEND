package com.civia.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

/** Sin bucket S3, los archivos se guardan en la base de datos y sobreviven al disco del servidor. */
@SpringBootTest(properties = {"civia.storage.db.enabled=true", "civia.storage.db.max-file-mb=1", "civia.storage.db.max-total-mb=2"})
class DatabaseObjectStoreTest {

    @Autowired ObjectStore store;
    @Autowired StorageService storage;

    @Test
    void usesTheDatabaseWhenThereIsNoBucket() {
        assertThat(store).isInstanceOf(DatabaseObjectStore.class);
    }

    @Test
    void fileGoesToTheDatabaseAndComesBack() throws Exception {
        byte[] pdf = "%PDF-1.7 plano guardado en la base".getBytes(StandardCharsets.ISO_8859_1);
        StorageService.Stored stored = storage.store(new ByteArrayInputStream(pdf), storage.typeOf("plano.pdf"));

        // La copia de paso en el disco se borra: el archivo vive solo en la base.
        assertThat(Files.exists(storage.resolve(stored.storageKey()))).isFalse();
        assertThat(store.size(stored.storageKey())).isEqualTo(pdf.length);
        try (InputStream in = storage.open(stored.storageKey()).getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(pdf);
        }
        assertThat(storage.read(stored.storageKey())).isEqualTo(pdf);

        storage.deleteQuietly(stored.storageKey());
        assertThat(store.size(stored.storageKey())).isEqualTo(-1);
        assertThat(store.get(stored.storageKey())).isNull();
    }

    @Test
    void rejectsFilesOverThePlanLimit(@TempDir Path dir) throws Exception {
        Path big = dir.resolve("grande.bin");
        Files.write(big, new byte[(int) (1.5 * 1024 * 1024)]);
        assertThatThrownBy(() -> store.put("prueba/grande.bin", big, Files.size(big)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("MB");
        assertThat(store.size("prueba/grande.bin")).isEqualTo(-1);
    }
}
