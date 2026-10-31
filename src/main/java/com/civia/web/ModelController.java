package com.civia.web;

import com.civia.domain.Project;
import com.civia.domain.ProjectModel;
import com.civia.repo.ProjectModelRepository;
import com.civia.service.AuditService;
import com.civia.service.ModelQuantities;
import com.civia.service.ProjectAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Modelo 3D paramétrico del proyecto (modelador de CIVIA) y su cómputo de cantidades. */
@RestController
@RequestMapping("/api/v1/projects/{id}/model")
@Tag(name = "Modelado 3D")
public class ModelController {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_ELEMENTS = 5000;

    public record ModelDto(JsonNode model, int revision, Instant updatedAt, String updatedBy) {}

    /** {@code revision}: la que se cargó; si otra persona guardó después, se responde 409. */
    public record SaveModelRequest(JsonNode model, Integer revision) {}

    private final ProjectAccess access;
    private final ProjectModelRepository models;
    private final ObjectMapper json;
    private final AuditService audit;

    public ModelController(ProjectAccess access, ProjectModelRepository models, ObjectMapper json, AuditService audit) {
        this.access = access;
        this.models = models;
        this.json = json;
        this.audit = audit;
    }

    @Operation(summary = "Modelo 3D del proyecto (vacío si aún no tiene)")
    @GetMapping
    @Transactional(readOnly = true)
    public ModelDto get(@PathVariable UUID id) {
        access.view(id);
        return models.findByProjectId(id).map(this::dto)
                .orElseGet(() -> new ModelDto(json.createObjectNode().put("version", 1).set("elements", json.createArrayNode()), 0, null, null));
    }

    @Operation(summary = "Guardar el modelo 3D")
    @PutMapping
    @Transactional
    public ModelDto save(@PathVariable UUID id, @RequestBody SaveModelRequest req) {
        Project project = access.edit(id);
        JsonNode model = req.model();
        if (model == null || !model.isObject() || !model.path("elements").isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El modelo debe tener una lista de elementos.");
        }
        if (model.path("elements").size() > MAX_ELEMENTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El modelo tiene más de " + MAX_ELEMENTS + " elementos.");
        }
        String text;
        try {
            text = json.writeValueAsString(model);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modelo no válido.");
        }
        if (text.length() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El modelo supera 2 MB.");
        }
        ProjectModel m = models.findByProjectId(id).orElseGet(() -> {
            ProjectModel created = new ProjectModel();
            created.setProject(project);
            return created;
        });
        if (req.revision() != null && m.getId() != null && req.revision() != m.getRevision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Otra persona guardó el modelo mientras lo editabas. Recarga para ver sus cambios.");
        }
        m.setJson(text);
        m.setRevision(m.getRevision() + 1);
        m.setUpdatedBy(access.me());
        models.save(m);
        audit.activity(project, access.me(), "model.saved", "model", m.getId(),
                "Guardó el modelo 3D (" + model.path("elements").size() + " elementos)");
        return dto(m);
    }

    @Operation(summary = "Cómputo de cantidades del modelo 3D")
    @GetMapping("/quantities")
    @Transactional(readOnly = true)
    public ModelQuantities.Takeoff quantities(@PathVariable UUID id) {
        return ModelQuantities.of(get(id).model());
    }

    private ModelDto dto(ProjectModel m) {
        try {
            return new ModelDto(json.readTree(m.getJson()), m.getRevision(), m.getUpdatedAt(),
                    m.getUpdatedBy() == null ? null : m.getUpdatedBy().getFullName());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
