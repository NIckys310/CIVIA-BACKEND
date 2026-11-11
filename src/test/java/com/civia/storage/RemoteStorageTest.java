package com.civia.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Con un almacenamiento externo, los archivos no quedan en el disco del servidor. */
class RemoteStorageTest {

    /** Almacenamiento en memoria que se comporta como un bucket. */
    static final class MemoryStore implements ObjectStore {
        final Map<String, byte[]> objects = new HashMap<>();

        @Override
        public void put(String key, Path file, long size) {
            try {
                objects.put(key, Files.readAllBytes(file));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public InputStream get(String key) {
            byte[] b = objects.get(key);
            return b == null ? null : new ByteArrayInputStream(b);
        }

        @Override
        public long size(String key) {
            byte[] b = objects.get(key);
            return b == null ? -1 : b.length;
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }
    }

    @Test
    void filesGoToTheBucketAndComeBack(@TempDir Path dir) throws Exception {
        MemoryStore bucket = new MemoryStore();
        StorageService storage = new StorageService(dir.toString(), bucket);
        byte[] pdf = "%PDF-1.7 plano de prueba".getBytes(StandardCharsets.ISO_8859_1);
        StorageService.Stored stored = storage.store(new ByteArrayInputStream(pdf), storage.typeOf("plano.pdf"));

        assertThat(bucket.objects).containsKey(stored.storageKey());
        assertThat(Files.exists(storage.resolve(stored.storageKey()))).isFalse();
        assertThat(storage.exists(stored.storageKey())).isTrue();
        assertThat(storage.read(stored.storageKey())).isEqualTo(pdf);
        try (InputStream in = storage.open(stored.storageKey()).getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(pdf);
        }

        storage.deleteQuietly(stored.storageKey());
        assertThat(bucket.objects).isEmpty();
        assertThat(storage.exists(stored.storageKey())).isFalse();
    }

    @Test
    void withoutABucketFilesStayOnDisk(@TempDir Path dir) throws Exception {
        StorageService storage = new StorageService(dir.toString(), null);
        StorageService.Stored stored = storage.store(new ByteArrayInputStream("%PDF-1.7".getBytes(StandardCharsets.ISO_8859_1)), storage.typeOf("a.pdf"));
        assertThat(Files.exists(storage.resolve(stored.storageKey()))).isTrue();
        assertThat(storage.open(stored.storageKey()).contentLength()).isEqualTo(8);
    }
}
