package com.civia.web;

import com.civia.domain.*;
import com.civia.domain.Enums.DocumentKind;
import com.civia.domain.Enums.ProjectRole;
import com.civia.domain.Enums.TaskStatus;
import com.civia.repo.*;
import com.civia.service.AuditService;
import com.civia.service.ProjectAccess;
import com.civia.web.Dto.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Proyectos, miembros, fases, niveles y espacios. */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Proyectos")
public class ProjectController {

    private static final String[] COVERS = {"#C9A46A", "#8FA3C7", "#9C8A78", "#6F8F86", "#B07F6B", "#7D7FA6"};
    private static final String[] DEFAULT_PHASES = {"Concepto", "Anteproyecto", "Proyecto para licencia", "Proyecto ejecutivo", "Construcción", "Entrega"};

    private final ProjectAccess access;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectPhaseRepository phases;
    private final LevelRepository levels;
    private final RoomRepository rooms;
    private final DocumentRepository documents;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final ClientRepository clients;
    private final ActivityLogRepository activity;
    private final AuditService audit;

    public ProjectController(ProjectAccess access, ProjectRepository projects, ProjectMemberRepository members,
                             ProjectPhaseRepository phases, LevelRepository levels, RoomRepository rooms,
                             DocumentRepository documents, TaskRepository tasks, UserRepository users,
                             ClientRepository clients, ActivityLogRepository activity, AuditService audit) {
        this.access = access;
        this.projects = projects;
        this.members = members;
        this.phases = phases;
        this.levels = levels;
        this.rooms = rooms;
        this.documents = documents;
        this.tasks = tasks;
        this.users = users;
        this.clients = clients;
        this.activity = activity;
        this.audit = audit;
    }

    ProjectStats stats(UUID id) {
        return new ProjectStats(
                documents.countByProjectIdAndKind(id, DocumentKind.PLAN),
                documents.countByProjectIdAndKind(id, DocumentKind.MODEL_3D),
                documents.countByProjectIdAndKind(id, DocumentKind.PHOTO),
                documents.countByProjectIdAndKind(id, DocumentKind.DOCUMENT),
                tasks.countByProjectIdAndStatusNot(id, TaskStatus.DONE),
                members.countByProjectId(id));
    }

    private ProjectDto dto(Project p, UUID userId) {
        return Dto.project(p, access.roleIn(p.getId(), userId).name(), stats(p.getId()));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ProjectDto> list() {
        User me = access.me();
        return projects.findAccessible(me.getId()).stream().map(p -> dto(p, me.getId())).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ProjectDto create(@RequestBody ProjectRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El proyecto necesita un nombre.");
        }
        User me = access.me();
        Project p = new Project();
        p.setOwner(me);
        p.setOrganization(me.getOrganization());
        p.setCoverColor(COVERS[(int) (Math.random() * COVERS.length)]);
        apply(p, req);
        if (p.getCode() == null || p.getCode().isBlank()) {
            p.setCode("PRJ-" + Integer.toHexString((int) (System.currentTimeMillis() & 0xfffff)).toUpperCase(Locale.ROOT));
        }
        projects.save(p);

        ProjectMember owner = new ProjectMember();
        owner.setProject(p);
        owner.setUser(me);
        owner.setRole(ProjectRole.OWNER);
        members.save(owner);

        for (int i = 0; i < DEFAULT_PHASES.length; i++) {
            ProjectPhase ph = new ProjectPhase();
            ph.setProject(p);
            ph.setName(DEFAULT_PHASES[i]);
            ph.setSortOrder(i);
            ph.setStatus(i == 0 ? "EN_CURSO" : "PENDIENTE");
            phases.save(ph);
        }
        if (p.getPhase() == null) p.setPhase(DEFAULT_PHASES[0]);
        audit.activity(p, me, "project.created", "project", p.getId(), "Creó el proyecto «" + p.getName() + "»");
        return Dto.project(p, ProjectRole.OWNER.name(), stats(p.getId()));
    }

    private void apply(Project p, ProjectRequest r) {
        if (r.name() != null && !r.name().isBlank()) p.setName(r.name().strip());
        if (r.description() != null) p.setDescription(r.description());
        if (r.type() != null) p.setType(r.type());
        if (r.status() != null) p.setStatus(Enums.ProjectStatus.valueOf(r.status()));
        if (r.phase() != null) p.setPhase(r.phase());
        if (r.location() != null) p.setLocation(r.location());
        if (r.address() != null) p.setAddress(r.address());
        if (r.latitude() != null) p.setLatitude(r.latitude());
        if (r.longitude() != null) p.setLongitude(r.longitude());
        if (r.lotArea() != null) p.setLotArea(r.lotArea());
        if (r.builtArea() != null) p.setBuiltArea(r.builtArea());
        if (r.floors() != null) p.setFloors(r.floors());
        if (r.budgetEstimate() != null) p.setBudgetEstimate(r.budgetEstimate());
        if (r.currency() != null) p.setCurrency(r.currency());
        if (r.startDate() != null) p.setStartDate(r.startDate());
        if (r.dueDate() != null) p.setDueDate(r.dueDate());
        if (r.coverColor() != null) p.setCoverColor(r.coverColor());
        if (r.code() != null) p.setCode(r.code());
        if (r.archived() != null) p.setArchived(r.archived());
        if (r.clientId() != null) p.setClient(clients.findById(r.clientId()).orElse(null));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ProjectDto get(@PathVariable UUID id) {
        return dto(access.view(id), access.me().getId());
    }

    @PatchMapping("/{id}")
    @Transactional
    public ProjectDto update(@PathVariable UUID id, @RequestBody ProjectRequest req) {
        Project p = access.edit(id);
        apply(p, req);
        audit.activity(p, access.me(), "project.updated", "project", id, "Actualizó los datos del proyecto");
        return dto(p, access.me().getId());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> archive(@PathVariable UUID id) {
        Project p = access.own(id);
        p.setArchived(true);
        audit.activity(p, access.me(), "project.archived", "project", id, "Archivó el proyecto");
        return Map.of("archived", true);
    }

    // ------------------------------------------------------------------ miembros

    @GetMapping("/{id}/members")
    @Transactional(readOnly = true)
    public List<MemberDto> listMembers(@PathVariable UUID id) {
        access.view(id);
        return members.findByProjectId(id).stream().map(m -> new MemberDto(m.getId(), m.getUser().getId(),
                m.getUser().getFullName(), m.getUser().getEmail(), m.getRole().name(), m.getUser().getAvatarColor())).toList();
    }

    @PostMapping("/{id}/members")
    @Transactional
    public MemberDto addMember(@PathVariable UUID id, @RequestBody AddMemberRequest req) {
        Project p = access.own(id);
        User u = users.findByEmail(req.email() == null ? "" : req.email().strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay ninguna cuenta CIVIA con ese correo."));
        if (members.findByProjectIdAndUserId(id, u.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa persona ya es miembro del proyecto.");
        }
        ProjectRole role = req.role() == null ? ProjectRole.EDITOR : ProjectRole.valueOf(req.role());
        if (role == ProjectRole.OWNER) role = ProjectRole.EDITOR;
        ProjectMember m = new ProjectMember();
        m.setProject(p);
        m.setUser(u);
        m.setRole(role);
        members.save(m);
        audit.notify(u, "project", "Te agregaron a un proyecto", access.me().getFullName() + " te agregó a «" + p.getName() + "».", "#/projects/" + id);
        audit.activity(p, access.me(), "member.added", "user", u.getId(), "Agregó a " + u.getFullName() + " como " + role);
        return new MemberDto(m.getId(), u.getId(), u.getFullName(), u.getEmail(), role.name(), u.getAvatarColor());
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @Transactional
    public Map<String, Object> removeMember(@PathVariable UUID id, @PathVariable UUID memberId) {
        Project p = access.own(id);
        ProjectMember m = members.findById(memberId).filter(x -> x.getProject().getId().equals(id))
                .orElseThrow(() -> ProjectAccess.notFound("Miembro"));
        if (m.getRole() == ProjectRole.OWNER) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede quitar al propietario.");
        members.delete(m);
        audit.activity(p, access.me(), "member.removed", "user", m.getUser().getId(), "Quitó a " + m.getUser().getFullName());
        return Map.of("removed", true);
    }

    // ------------------------------------------------------------------ fases

    @GetMapping("/{id}/phases")
    @Transactional(readOnly = true)
    public List<PhaseDto> listPhases(@PathVariable UUID id) {
        access.view(id);
        return phases.findByProjectIdOrderBySortOrderAsc(id).stream()
                .map(f -> new PhaseDto(f.getId(), f.getName(), f.getSortOrder(), f.getStatus(), f.getStartDate(), f.getEndDate())).toList();
    }

    @PatchMapping("/{id}/phases/{phaseId}")
    @Transactional
    public PhaseDto updatePhase(@PathVariable UUID id, @PathVariable UUID phaseId, @RequestBody PhaseDto req) {
        Project p = access.edit(id);
        ProjectPhase f = phases.findById(phaseId).filter(x -> x.getProject().getId().equals(id)).orElseThrow(() -> ProjectAccess.notFound("Fase"));
        if (req.name() != null) f.setName(req.name());
        if (req.status() != null) {
            f.setStatus(req.status());
            if ("EN_CURSO".equals(req.status())) p.setPhase(f.getName());
        }
        if (req.startDate() != null) f.setStartDate(req.startDate());
        if (req.endDate() != null) f.setEndDate(req.endDate());
        return new PhaseDto(f.getId(), f.getName(), f.getSortOrder(), f.getStatus(), f.getStartDate(), f.getEndDate());
    }

    // ------------------------------------------------------------------ niveles y espacios

    @GetMapping("/{id}/levels")
    @Transactional(readOnly = true)
    public List<LevelDto> listLevels(@PathVariable UUID id) {
        access.view(id);
        return levels.findByProjectIdOrderBySortOrderAsc(id).stream()
                .map(l -> new LevelDto(l.getId(), l.getName(), l.getElevation(), l.getHeight(), l.getSortOrder())).toList();
    }

    @PostMapping("/{id}/levels")
    @Transactional
    public LevelDto addLevel(@PathVariable UUID id, @RequestBody LevelDto req) {
        Project p = access.edit(id);
        Level l = new Level();
        l.setProject(p);
        l.setName(req.name() == null || req.name().isBlank() ? "Nivel" : req.name());
        l.setElevation(req.elevation());
        l.setHeight(req.height());
        l.setSortOrder(req.sortOrder());
        levels.save(l);
        return new LevelDto(l.getId(), l.getName(), l.getElevation(), l.getHeight(), l.getSortOrder());
    }

    @DeleteMapping("/{id}/levels/{levelId}")
    @Transactional
    public Map<String, Object> deleteLevel(@PathVariable UUID id, @PathVariable UUID levelId) {
        access.edit(id);
        levels.findById(levelId).filter(x -> x.getProject().getId().equals(id)).ifPresent(l -> {
            rooms.findByProjectIdOrderByNameAsc(id).stream().filter(r -> r.getLevel() != null && r.getLevel().getId().equals(levelId))
                    .forEach(r -> r.setLevel(null));
            levels.delete(l);
        });
        return Map.of("deleted", true);
    }

    @GetMapping("/{id}/rooms")
    @Transactional(readOnly = true)
    public List<RoomDto> listRooms(@PathVariable UUID id) {
        access.view(id);
        return rooms.findByProjectIdOrderByNameAsc(id).stream().map(ProjectController::room).toList();
    }

    @PostMapping("/{id}/rooms")
    @Transactional
    public RoomDto addRoom(@PathVariable UUID id, @RequestBody RoomDto req) {
        Project p = access.edit(id);
        Room r = new Room();
        r.setProject(p);
        r.setName(req.name() == null || req.name().isBlank() ? "Espacio" : req.name());
        r.setRoomType(req.roomType());
        r.setArea(req.area());
        r.setNotes(req.notes());
        if (req.levelId() != null) r.setLevel(levels.findById(req.levelId()).filter(l -> l.getProject().getId().equals(id)).orElse(null));
        rooms.save(r);
        return room(r);
    }

    @DeleteMapping("/{id}/rooms/{roomId}")
    @Transactional
    public Map<String, Object> deleteRoom(@PathVariable UUID id, @PathVariable UUID roomId) {
        access.edit(id);
        rooms.findById(roomId).filter(x -> x.getProject().getId().equals(id)).ifPresent(rooms::delete);
        return Map.of("deleted", true);
    }

    private static RoomDto room(Room r) {
        return new RoomDto(r.getId(), r.getName(), r.getRoomType(), r.getArea(), r.getNotes(),
                r.getLevel() == null ? null : r.getLevel().getId(), r.getLevel() == null ? null : r.getLevel().getName());
    }

    // ------------------------------------------------------------------ bitácora

    @GetMapping("/{id}/activity")
    @Transactional(readOnly = true)
    public List<ActivityDto> activity(@PathVariable UUID id) {
        access.view(id);
        return activity.findTop100ByProjectIdOrderByCreatedAtDesc(id).stream().map(Dto::activity).toList();
    }
}
