package com.civia.storage;

import java.io.InputStream;
import java.nio.file.Path;

/** Almacenamiento de objetos externo (Cloudflare R2, S3 u otro compatible) donde quedan los archivos subidos. */
public interface ObjectStore {

    void put(String key, Path file, long size);

    /** Contenido del objeto, o null si no existe. */
    InputStream get(String key);

    /** Tamaño en bytes, o -1 si no existe. */
    long size(String key);

    void delete(String key);
}
