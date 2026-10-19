package com.civia.web;

import com.civia.domain.*;
import com.civia.domain.Enums.DocumentKind;
import com.civia.repo.*;
import com.civia.service.AuditService;
import com.civia.service.ProjectAccess;
import com.civia.storage.StorageService;
import com.civia.web.Dto.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Archivos del proyecto: planos, modelos 3D, fotos de la cámara y documentos, con versiones. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Archivos")
public class DocumentController {

    private final ProjectAccess access;
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final PhotoMetadataRepository photos;
    private final MeasurementRepository measurements;
    private final ModelAnnotationRepository annotations;
    private final AiAnalysisRepository analyses;
    private final StorageService storage;
    private final AuditService audit;

    public DocumentController(ProjectAccess access, DocumentRepository documents, DocumentVersionRepository versions,
                              PhotoMetadataRepository photos, MeasurementRepository measurements,
                              ModelAnnotationRepository annotations, AiAnalysisRepository analyses,
                              StorageService storage, AuditService audit) {
        this.access = access;
        this.documents = documents;
        this.versions = versions;
        this.photos = photos;
        this.measurements = measurements;
        this.annotations = annotations;
        this.analyses = analyses;
        this.storage = storage;
        this.audit = audit;
    }

    private DocumentDto dto(Document d) {
        return Dto.document(d, Dto.brief(analyses.findFirstByDocumentIdOrderByCreatedAtDesc(d.getId()).orElse(null)));
    }

    @GetMapping("/projects/{id}/documents")
    @Transactional(readOnly = true)
    public List<DocumentDto> list(@PathVariable UUID id, @RequestParam(required = false) String kind) {
        access.view(id);
        List<Document> list = kind == null ? documents.findByProjectIdOrderByCreatedAtDesc(id)
                : documents.findByProjectIdAndKindOrderByCreatedAtDesc(id, DocumentKind.valueOf(kind));
        return list.stream().map(this::dto).toList();
    }

    @PostMapping(path = "/projects/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public DocumentDto upload(@PathVariable UUID id,
                              @RequestPart("file") MultipartFile file,
                              @RequestParam(required = false) String kind,
                              @RequestParam(required = false) String description,
                              @RequestParam(required = false) String planType,
                              @RequestParam(required = false) String scale,
                              @RequestParam(required = false) String discipline,
                              @RequestParam(required = false) Double latitude,
                              @RequestParam(required = false) Double longitude,
                              @RequestParam(required = false) String deviceInfo) {
        Project p = access.edit(id);
        User me = access.me();
        String original = cleanName(file.getOriginalFilename());
        StorageService.FileType type = storage.typeOf(original);
        StorageService.Stored stored = store(file, type);

        Document d = new Document();
        d.setProject(p);
        d.setUploadedBy(me);
        d.setName(original);
        d.setKind(kind == null || kind.isBlank() ? type.defaultKind() : DocumentKind.valueOf(kind));
        d.setMediaType(type.mediaType());
        d.setExtension(type.extension());
        d.setSizeBytes(stored.size());
        d.setSha256(stored.sha256());
        d.setStorageKey(stored.storageKey());
        d.setDescription(description);
        d.setPlanType(planType);
        d.setScale(scale);
        d.setDiscipline(discipline);
        documents.save(d);
        saveVersion(d, stored, me, "Versión inicial");

        if (d.getKind() == DocumentKind.PHOTO) {
            PhotoMetadata meta = new PhotoMetadata();
            meta.setDocument(d);
            meta.setLatitude(latitude);
            meta.setLongitude(longitude);
            meta.setDeviceInfo(deviceInfo);
            meta.setTakenAt(Instant.now());
            photos.save(meta);
        }
        audit.activity(p, me, "document.uploaded", "document", d.getId(),
                "Subió " + label(d.getKind()) + " «" + d.getName() + "»");
        return dto(d);
    }

    @PostMapping(path = "/documents/{docId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public DocumentDto newVersion(@PathVariable UUID docId, @RequestPart("file") MultipartFile file,
                                  @RequestParam(required = false) String note) {
        Document d = editable(docId);
        StorageService.FileType type = storage.typeOf(cleanName(file.getOriginalFilename()));
        if (!type.extension().equals(d.getExtension())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva versión debe ser del mismo formato (." + d.getExtension() + ").");
        }
        StorageService.Stored stored = store(file, type);
        d.setCurrentVersion(d.getCurrentVersion() + 1);
        d.setStorageKey(stored.storageKey());
        d.setSizeBytes(stored.size());
        d.setSha256(stored.sha256());
        saveVersion(d, stored, access.me(), note);
        audit.activity(d.getProject(), access.me(), "document.version", "document", d.getId(),
                "Subió la versión " + d.getCurrentVersion() + " de «" + d.getName() + "»");
        return dto(d);
    }

    @GetMapping("/documents/{docId}/versions")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listVersions(@PathVariable UUID docId) {
        viewable(docId);
        return versions.findByDocumentIdOrderByVersionDesc(docId).stream().map(v -> Map.<String, Object>of(
                "id", v.getId(), "version", v.getVersion(), "sizeBytes", v.getSizeBytes(),
                "sha256", v.getSha256() == null ? "" : v.getSha256(), "note", v.getNote() == null ? "" : v.getNote(),
                "uploadedBy", v.getUploadedBy() == null ? "" : v.getUploadedBy().getFullName(), "createdAt", v.getCreatedAt())).toList();
    }

    @GetMapping("/documents/{docId}")
    @Transactional(readOnly = true)
    public DocumentDto get(@PathVariable UUID docId) {
        return dto(viewable(docId));
    }

    @PatchMapping("/documents/{docId}")
    @Transactional
    public DocumentDto update(@PathVariable UUID docId, @RequestBody DocumentUpdate req) {
        Document d = editable(docId);
        if (req.name() != null && !req.name().isBlank()) d.setName(cleanName(req.name()));
        if (req.description() != null) d.setDescription(req.description());
        if (req.planType() != null) d.setPlanType(req.planType());
        if (req.scale() != null) d.setScale(req.scale());
        if (req.discipline() != null) d.setDiscipline(req.discipline());
        return dto(d);
    }

    @DeleteMapping("/documents/{docId}")
    @Transactional
    public Map<String, Object> delete(@PathVariable UUID docId) {
        Document d = editable(docId);
        List<DocumentVersion> all = versions.findByDocumentIdOrderByVersionDesc(docId);
        photos.findByDocumentId(docId).ifPresent(photos::delete);
        measurements.findByDocumentId(docId).forEach(m -> m.setDocument(null));
        annotations.findByDocumentIdOrderByCreatedAtAsc(docId).forEach(annotations::delete);
        analyses.findByProjectIdOrderByCreatedAtDesc(d.getProject().getId()).stream()
                .filter(a -> a.getDocument() != null && a.getDocument().getId().equals(docId)).forEach(a -> a.setDocument(null));
        versions.deleteAll(all);
        documents.delete(d);
        all.forEach(v -> storage.deleteQuietly(v.getStorageKey()));
        audit.activity(d.getProject(), access.me(), "document.deleted", "document", docId, "Eliminó «" + d.getName() + "»");
        return Map.of("deleted", true);
    }

    /** Descarga o vista en línea. Los archivos se sirven aislados (sandbox, nosniff). */
    @GetMapping("/documents/{docId}/file")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> file(@PathVariable UUID docId, @RequestParam(defaultValue = "false") boolean download) {
        Document d = viewable(docId);
        FileSystemResource res = new FileSystemResource(storage.resolve(d.getStorageKey()));
        if (!res.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El archivo ya no está en el almacenamiento.");
        boolean inline = !download && (d.getMediaType().startsWith("image/") || d.getMediaType().equals("application/pdf")
                || d.getKind() == DocumentKind.MODEL_3D);
        ContentDisposition cd = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(d.getName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.getMediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .eTag("\"" + d.getSha256() + "\"")
                .body(res);
    }

    // ------------------------------------------------------------------ mediciones (cámara)

    @GetMapping("/projects/{id}/measurements")
    @Transactional(readOnly = true)
    public List<MeasurementDto> listMeasurements(@PathVariable UUID id) {
        access.view(id);
        return measurements.findByProjectIdOrderByCreatedAtDesc(id).stream().map(DocumentController::measurement).toList();
    }

    @PostMapping("/projects/{id}/measurements")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public MeasurementDto addMeasurement(@PathVariable UUID id, @RequestBody MeasurementRequest req) {
        Project p = access.edit(id);
        Measurement m = new Measurement();
        m.setProject(p);
        m.setAuthor(access.me());
        if (req.documentId() != null) {
            m.setDocument(documents.findById(req.documentId()).filter(d -> d.getProject().getId().equals(id)).orElse(null));
        }
        m.setLabel(req.label());
        m.setValue(req.value());
        m.setUnit(req.unit() == null ? "m" : req.unit());
        m.setReferenceLength(req.referenceLength());
        m.setPointsJson(req.pointsJson());
        measurements.save(m);
        audit.activity(p, access.me(), "measurement.created", "measurement", m.getId(),
                String.format("Midió %s: %.2f %s", req.label() == null ? "una distancia" : req.label(), req.value(), m.getUnit()));
        return measurement(m);
    }

    @DeleteMapping("/projects/{id}/measurements/{mId}")
    @Transactional
    public Map<String, Object> deleteMeasurement(@PathVariable UUID id, @PathVariable UUID mId) {
        access.edit(id);
        measurements.findById(mId).filter(m -> m.getProject().getId().equals(id)).ifPresent(measurements::delete);
        return Map.of("deleted", true);
    }

    private static MeasurementDto measurement(Measurement m) {
        return new MeasurementDto(m.getId(), m.getDocument() == null ? null : m.getDocument().getId(), m.getLabel(), m.getValue(),
                m.getUnit(), m.getReferenceLength(), m.getPointsJson(), m.getAuthor() == null ? null : m.getAuthor().getFullName(), m.getCreatedAt());
    }

    // ------------------------------------------------------------------ notas en modelos 3D

    @GetMapping("/documents/{docId}/annotations")
    @Transactional(readOnly = true)
    public List<AnnotationDto> listAnnotations(@PathVariable UUID docId) {
        viewable(docId);
        return annotations.findByDocumentIdOrderByCreatedAtAsc(docId).stream().map(DocumentController::annotation).toList();
    }

    @PostMapping("/documents/{docId}/annotations")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AnnotationDto annotate(@PathVariable UUID docId, @RequestBody AnnotationRequest req) {
        Document d = viewable(docId);
        if (req.text() == null || req.text().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe la nota.");
        ModelAnnotation a = new ModelAnnotation();
        a.setDocument(d);
        a.setAuthor(access.me());
        a.setX(req.x());
        a.setY(req.y());
        a.setZ(req.z());
        a.setText(req.text());
        annotations.save(a);
        audit.activity(d.getProject(), access.me(), "annotation.created", "document", docId, "Anotó el modelo «" + d.getName() + "»");
        return annotation(a);
    }

    @PatchMapping("/documents/{docId}/annotations/{aId}")
    @Transactional
    public AnnotationDto resolveAnnotation(@PathVariable UUID docId, @PathVariable UUID aId, @RequestBody Map<String, Boolean> body) {
        viewable(docId);
        ModelAnnotation a = annotations.findById(aId).filter(x -> x.getDocument().getId().equals(docId)).orElseThrow(() -> ProjectAccess.notFound("Nota"));
        a.setResolved(Boolean.TRUE.equals(body.get("resolved")));
        return annotation(a);
    }

    private static AnnotationDto annotation(ModelAnnotation a) {
        return new AnnotationDto(a.getId(), a.getX(), a.getY(), a.getZ(), a.getText(), a.isResolved(),
                a.getAuthor() == null ? null : a.getAuthor().getFullName(), a.getCreatedAt());
    }

    // ------------------------------------------------------------------ utilidades

    Document viewable(UUID docId) {
        Document d = documents.findById(docId).orElseThrow(() -> ProjectAccess.notFound("Archivo"));
        access.view(d.getProject().getId());
        return d;
    }

    private Document editable(UUID docId) {
        Document d = documents.findById(docId).orElseThrow(() -> ProjectAccess.notFound("Archivo"));
        access.edit(d.getProject().getId());
        return d;
    }

    private StorageService.Stored store(MultipartFile file, StorageService.FileType type) {
        try {
            return storage.store(file.getInputStream(), type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void saveVersion(Document d, StorageService.Stored stored, User me, String note) {
        DocumentVersion v = new DocumentVersion();
        v.setDocument(d);
        v.setVersion(d.getCurrentVersion());
        v.setStorageKey(stored.storageKey());
        v.setSizeBytes(stored.size());
        v.setSha256(stored.sha256());
        v.setUploadedBy(me);
        v.setNote(note);
        versions.save(v);
    }

    /** Nombre visible seguro: sin rutas ni caracteres de control. */
    static String cleanName(String name) {
        if (name == null || name.isBlank()) return "archivo";
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").strip();
    }

    private static String label(DocumentKind k) {
        return switch (k) {
            case PLAN -> "el plano";
            case MODEL_3D -> "el modelo 3D";
            case PHOTO -> "la foto";
            case DOCUMENT -> "el documento";
        };
    }
}
