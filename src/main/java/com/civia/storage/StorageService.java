package com.civia.storage;

import com.civia.domain.Enums.DocumentKind;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Almacenamiento de archivos en disco, fuera de la carpeta web y con nombres aleatorios.
 *
 * <p>Solo se aceptan formatos de arquitectura conocidos y se verifica la firma real del archivo
 * (bytes mágicos), así un ejecutable renombrado a .pdf se rechaza.
 */
@Service
public class StorageService {

    public record FileType(String extension, String mediaType, DocumentKind defaultKind, String magic, boolean text) {}

    public record Stored(String storageKey, long size, String sha256) {}

    private static final Map<String, FileType> TYPES = Map.ofEntries(
            t("pdf", "application/pdf", DocumentKind.PLAN, "%PDF", false),
            t("png", "image/png", DocumentKind.PHOTO, "\u0089PNG", false),
            t("jpg", "image/jpeg", DocumentKind.PHOTO, "ÿØÿ", false),
            t("jpeg", "image/jpeg", DocumentKind.PHOTO, "ÿØÿ", false),
            t("webp", "image/webp", DocumentKind.PHOTO, "RIFF", false),
            t("gif", "image/gif", DocumentKind.PHOTO, "GIF8", false),
            t("tif", "image/tiff", DocumentKind.PLAN, null, false),
            t("tiff", "image/tiff", DocumentKind.PLAN, null, false),
            t("dxf", "application/dxf", DocumentKind.PLAN, null, true),
            t("dwg", "application/acad", DocumentKind.PLAN, "AC10", false),
            t("glb", "model/gltf-binary", DocumentKind.MODEL_3D, "glTF", false),
            t("gltf", "model/gltf+json", DocumentKind.MODEL_3D, null, true),
            t("obj", "model/obj", DocumentKind.MODEL_3D, null, true),
            t("mtl", "model/mtl", DocumentKind.MODEL_3D, null, true),
            t("stl", "model/stl", DocumentKind.MODEL_3D, null, false),
            t("fbx", "application/octet-stream", DocumentKind.MODEL_3D, null, false),
            t("ifc", "application/x-step", DocumentKind.MODEL_3D, "ISO-10303-21", true),
            t("skp", "application/octet-stream", DocumentKind.MODEL_3D, null, false),
            t("rvt", "application/octet-stream", DocumentKind.MODEL_3D, "ÐÏ\u0011à", false),
            t("3dm", "application/octet-stream", DocumentKind.MODEL_3D, null, false),
            t("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", DocumentKind.DOCUMENT, "PK\u0003\u0004", false),
            t("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", DocumentKind.DOCUMENT, "PK\u0003\u0004", false),
            t("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", DocumentKind.DOCUMENT, "PK\u0003\u0004", false),
            t("zip", "application/zip", DocumentKind.DOCUMENT, "PK\u0003\u0004", false),
            t("doc", "application/msword", DocumentKind.DOCUMENT, "ÐÏ\u0011à", false),
            t("xls", "application/vnd.ms-excel", DocumentKind.DOCUMENT, "ÐÏ\u0011à", false),
            t("txt", "text/plain", DocumentKind.DOCUMENT, null, true),
            t("csv", "text/csv", DocumentKind.DOCUMENT, null, true),
            t("md", "text/markdown", DocumentKind.DOCUMENT, null, true));

    private static Map.Entry<String, FileType> t(String ext, String mt, DocumentKind kind, String magic, boolean text) {
        return Map.entry(ext, new FileType(ext, mt, kind, magic, text));
    }

    private final Path root;

    public StorageService(@Value("${civia.storage.dir}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public FileType typeOf(String fileName) {
        FileType type = TYPES.get(extension(fileName));
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Formato no permitido. Se aceptan planos (PDF, DWG, DXF, imágenes), modelos 3D (GLB, glTF, OBJ, STL, FBX, IFC, SKP, RVT) y documentos de oficina.");
        }
        return type;
    }

    /** Guarda el archivo en streaming (sin cargarlo en memoria) calculando su SHA-256. */
    public Stored store(InputStream input, FileType type) {
        String key = LocalDate.now().getYear() + "/" + String.format("%02d", LocalDate.now().getMonthValue()) + "/" + UUID.randomUUID() + ".bin";
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (BufferedInputStream buffered = new BufferedInputStream(input, 64 * 1024)) {
                buffered.mark(8192);
                byte[] head = buffered.readNBytes(8192);
                verifySignature(head, type);
                buffered.reset();
                try (DigestInputStream in = new DigestInputStream(buffered, digest);
                     OutputStream out = Files.newOutputStream(target)) {
                    long size = in.transferTo(out);
                    return new Stored(key, size, HexFormat.of().formatHex(digest.digest()));
                }
            }
        } catch (IOException e) {
            deleteQuietly(key);
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (ResponseStatusException e) {
            deleteQuietly(key);
            throw e;
        }
    }

    private static void verifySignature(byte[] head, FileType type) {
        if (head.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo está vacío.");
        }
        if (type.magic() != null) {
            byte[] magic = type.magic().getBytes(StandardCharsets.ISO_8859_1);
            if (head.length < magic.length || !Arrays.equals(Arrays.copyOf(head, magic.length), magic)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "El contenido no corresponde a un archivo ." + type.extension() + " válido.");
            }
        }
        if (type.text()) {
            for (byte b : head) {
                if (b == 0) {
                    throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                            "El archivo ." + type.extension() + " debería ser de texto.");
                }
            }
        }
        // Un archivo de texto nunca debe ser HTML/JS que se pueda ejecutar si alguien lo abre.
        String start = new String(head, 0, Math.min(head.length, 512), StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        if (start.contains("<script") || start.contains("<html")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Contenido HTML/JavaScript no permitido.");
        }
    }

    public Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta inválida");
        return p;
    }

    public byte[] read(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void deleteQuietly(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException | ResponseStatusException ignored) {
            // Se reintentará en la limpieza periódica.
        }
    }
}
