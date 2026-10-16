package com.civia.web;

import com.civia.ai.CiviaAi;
import com.civia.domain.Client;
import com.civia.domain.Enums;
import com.civia.domain.Notification;
import com.civia.domain.Project;
import com.civia.domain.User;
import com.civia.repo.*;
import com.civia.service.ProjectAccess;
import com.civia.web.Dto.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Panel de inicio, notificaciones, clientes y estado del servicio. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Panel")
public class HomeController {

    private final ProjectAccess access;
    private final ProjectRepository projects;
    private final DocumentRepository documents;
    private final TaskRepository tasks;
    private final NotificationRepository notifications;
    private final ActivityLogRepository activity;
    private final ClientRepository clients;
    private final CiviaAi ai;

    public HomeController(ProjectAccess access, ProjectRepository projects, DocumentRepository documents, TaskRepository tasks,
                          NotificationRepository notifications, ActivityLogRepository activity, ClientRepository clients, CiviaAi ai) {
        this.access = access;
        this.projects = projects;
        this.documents = documents;
        this.tasks = tasks;
        this.notifications = notifications;
        this.activity = activity;
        this.clients = clients;
        this.ai = ai;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("service", "CIVIA AI", "status", "ok", "ai", ai.engineName(), "time", Instant.now().toString());
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        User me = access.me();
        List<Project> mine = projects.findAccessible(me.getId()).stream().filter(p -> !p.isArchived()).toList();
        long plans = 0, models = 0, photos = 0;
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Enums.ProjectStatus s : Enums.ProjectStatus.values()) byStatus.put(s.name(), 0L);
        double area = 0;
        for (Project p : mine) {
            plans += documents.countByProjectIdAndKind(p.getId(), Enums.DocumentKind.PLAN);
            models += documents.countByProjectIdAndKind(p.getId(), Enums.DocumentKind.MODEL_3D);
            photos += documents.countByProjectIdAndKind(p.getId(), Enums.DocumentKind.PHOTO);
            byStatus.merge(p.getStatus().name(), 1L, Long::sum);
            if (p.getBuiltArea() != null) area += p.getBuiltArea();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", Map.of("fullName", me.getFullName(), "avatarColor", me.getAvatarColor() == null ? "#C9A46A" : me.getAvatarColor()));
        out.put("projects", mine.size());
        out.put("plans", plans);
        out.put("models", models);
        out.put("photos", photos);
        out.put("builtArea", Math.round(area));
        out.put("storageBytes", documents.storageUsedBy(me.getId()));
        out.put("byStatus", byStatus);
        out.put("myTasks", tasks.findByAssigneeIdAndStatusNotOrderByDueDateAsc(me.getId(), Enums.TaskStatus.DONE).stream().limit(8).map(Dto::task).toList());
        out.put("recentProjects", mine.stream().limit(6).map(p -> Map.of("id", p.getId(), "name", p.getName(),
                "phase", p.getPhase() == null ? "" : p.getPhase(), "coverColor", p.getCoverColor() == null ? "#C9A46A" : p.getCoverColor(),
                "status", p.getStatus().name(), "location", p.getLocation() == null ? "" : p.getLocation())).toList());
        out.put("activity", activity.recentFor(me.getId()).stream().map(Dto::activity).toList());
        out.put("unread", notifications.countByUserIdAndReadAtIsNull(me.getId()));
        out.put("ai", Map.of("engine", ai.engineName(), "llm", ai.llmEnabled(),
                "articles", ai.library().articles().size(), "terms", ai.library().terms().size()));
        return out;
    }

    @GetMapping("/notifications")
    @Transactional(readOnly = true)
    public Map<String, Object> notifications() {
        UUID me = access.me().getId();
        return Map.of("unread", notifications.countByUserIdAndReadAtIsNull(me),
                "items", notifications.findTop50ByUserIdOrderByCreatedAtDesc(me).stream().map(Dto::notification).toList());
    }

    @PostMapping("/notifications/read-all")
    @Transactional
    public Map<String, Object> readAll() {
        Instant now = Instant.now();
        notifications.findByUserIdAndReadAtIsNull(access.me().getId()).forEach(n -> n.setReadAt(now));
        return Map.of("unread", 0);
    }

    @PostMapping("/notifications/{id}/read")
    @Transactional
    public Map<String, Object> read(@PathVariable UUID id) {
        Notification n = notifications.findById(id).filter(x -> x.getUser().getId().equals(access.me().getId()))
                .orElseThrow(() -> ProjectAccess.notFound("Notificación"));
        n.setReadAt(Instant.now());
        return Map.of("read", true);
    }

    @GetMapping("/clients")
    @Transactional(readOnly = true)
    public List<ClientDto> clients() {
        User me = access.me();
        return clients.findByOrganizationIdOrderByNameAsc(me.getOrganization().getId()).stream()
                .map(c -> new ClientDto(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getCompany(), c.getNotes())).toList();
    }

    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ClientDto addClient(@RequestBody ClientRequest req) {
        if (req.name() == null || req.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cliente necesita un nombre.");
        Client c = new Client();
        c.setOrganization(access.me().getOrganization());
        c.setName(req.name().strip());
        c.setEmail(req.email());
        c.setPhone(req.phone());
        c.setCompany(req.company());
        c.setNotes(req.notes());
        clients.save(c);
        return new ClientDto(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getCompany(), c.getNotes());
    }
}
